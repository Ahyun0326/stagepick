package kr.hhplus.be.server.integration

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.facade.PaymentFacade
import kr.hhplus.be.server.domains.payment.domain.model.PaymentStatus
import kr.hhplus.be.server.domains.payment.infrastructure.persistence.SpringPaymentJpa
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.facade.PointFacade
import kr.hhplus.be.server.domains.point.domain.model.PointHistoryType
import kr.hhplus.be.server.domains.point.infrastructure.persistence.SpringPointHistoryJpa
import kr.hhplus.be.server.domains.point.infrastructure.persistence.SpringPointJpa
import kr.hhplus.be.server.domains.queue.application.facade.QueueFacade
import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.facade.ReservationFacade
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SpringSeatJpa
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 필수 통합 테스트 ① - 토큰 발급 → 좌석 예약 → 결제 완료 전체 흐름
 *
 * Facade 를 직접 호출하므로 HTTP 계층의 QueueTokenInterceptor 는 거치지 않는다.
 * 대신 인터셉터가 수행하는 토큰 검증(validateToken)을 명시적으로 호출해
 * "발급받은 토큰으로 예약한다" 는 연결고리를 유지한다.
 */
@DisplayName("[필수 통합] 토큰 발급 → 좌석 예약 → 결제 완료")
class ReservationPaymentFlowIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture

    @Autowired private lateinit var queueFacade: QueueFacade
    @Autowired private lateinit var pointFacade: PointFacade
    @Autowired private lateinit var reservationFacade: ReservationFacade
    @Autowired private lateinit var paymentFacade: PaymentFacade

    @Autowired private lateinit var springSeatJpa: SpringSeatJpa
    @Autowired private lateinit var springPointJpa: SpringPointJpa
    @Autowired private lateinit var springPointHistoryJpa: SpringPointHistoryJpa
    @Autowired private lateinit var springPaymentJpa: SpringPaymentJpa
    @Autowired private lateinit var seatHoldRepository: SeatHoldRepository
    @Autowired private lateinit var activeQueueRepository: ActiveQueueRepository

    @Test
    fun `토큰을 발급받아 좌석을 예약하고 결제하면 좌석이 확정되고 포인트가 차감된다`() {
        // given - 회원, 공연 회차, 좌석 2개, 포인트 충전
        val member = fixture.createMember()
        val memberId = member.id.shouldNotBeNull()
        val uuid = member.uuid
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)
        val totalPrice = SEAT_PRICE * SEAT_COUNT

        pointFacade.chargePoint(uuid, ChargePointRequest(CHARGE_AMOUNT))

        // when 1 - 대기열 토큰 발급 (활성 인원 < capacity 이므로 즉시 ACTIVE)
        val tokenResponse = queueFacade.issueToken(uuid, scheduleId)

        tokenResponse.status shouldBe AdmissionStatus.ACTIVE.name
        val token = tokenResponse.token.shouldNotBeNull()

        // when 2 - 토큰 검증 (HTTP 요청에서는 QueueTokenInterceptor 가 수행)
        shouldNotThrowAny { queueFacade.validateToken(uuid, scheduleId, token) }

        // when 3 - 좌석 예약
        val reservationId = reservationFacade
            .reserveSeat(uuid, ReservationRequest(scheduleId, seatIds))
            .reservationId

        // then 3 - 좌석이 HOLD 되고 예약에 연결되며, Redis 에 홀드가 등록된다
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe reservationId
        }
        seatHoldRepository.getHeldStatus(seatIds).forAll { it shouldBe true }

        // when 4 - 결제 (요청 금액은 좌석 가격 합과 정확히 일치해야 한다)
        val paymentResponse = paymentFacade.processPayment(
            uuid,
            PaymentRequest(reservationId = reservationId, point = totalPrice, scheduleId = scheduleId)
        )

        // then 4-1 - 결제 응답
        paymentResponse.amount shouldBe totalPrice
        paymentResponse.status shouldBe PaymentStatus.PAID.name

        // then 4-2 - 좌석 확정 + Redis 홀드 제거
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.RESERVED.name
            it.reservationId shouldBe reservationId
        }
        seatHoldRepository.getHeldStatus(seatIds).forAll { it shouldBe false }

        // then 4-3 - 포인트 차감 + 충전/사용 이력
        springPointJpa.findByMemberId(memberId).get().point shouldBe CHARGE_AMOUNT - totalPrice
        springPointHistoryJpa.findAll()
            .filter { it.memberId == memberId }
            .map { it.type } shouldContainExactly listOf(PointHistoryType.CHARGE.name, PointHistoryType.USE.name)

        // then 4-4 - 결제 레코드 저장
        val payment = springPaymentJpa.findByReservationId(reservationId).orElse(null).shouldNotBeNull()
        payment.price shouldBe totalPrice
        payment.status shouldBe PaymentStatus.PAID.name

        // then 4-5 - 결제 완료 후 활성 토큰 반납
        activeQueueRepository.findActive(scheduleId, uuid).shouldBeNull()
    }

    companion object {
        private const val SEAT_COUNT = 2
        private const val SEAT_PRICE = 50_000
        private const val CHARGE_AMOUNT = 200_000
    }
}
