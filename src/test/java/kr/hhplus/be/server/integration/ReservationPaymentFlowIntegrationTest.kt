package kr.hhplus.be.server.integration

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.common.exception.DuplicatePaymentException
import kr.hhplus.be.server.common.exception.QueueTokenInvalidException
import kr.hhplus.be.server.common.exception.ReservationNotFoundException
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
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
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
 *
 * 실패 케이스는 실제 Redis·DB 상태로 판단하는 것만 다룬다 (분기 자체는 단위 테스트 몫).
 * 결제 도중 실패 시 롤백은 PaymentFailureRollbackIntegrationTest 에서 다룬다.
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
    @Autowired private lateinit var waitingQueueRepository: WaitingQueueRepository
    @Autowired private lateinit var queueProperties: QueueProperties

    @Test
    fun `토큰을 발급받아 좌석을 예약하고 결제하면 좌석이 확정되고 포인트가 차감된다`() {
        // given - 회원, 공연 회차, 좌석 2개, 포인트 충전
        val member = fixture.createMember()
        val memberId = member.id.shouldNotBeNull()
        val uuid = member.uuid
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)

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
            PaymentRequest(reservationId = reservationId, point = TOTAL_PRICE, scheduleId = scheduleId)
        )

        // then 4-1 - 결제 응답
        paymentResponse.amount shouldBe TOTAL_PRICE
        paymentResponse.status shouldBe PaymentStatus.PAID.name

        // then 4-2 - 좌석 확정 + Redis 홀드 제거
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.RESERVED.name
            it.reservationId shouldBe reservationId
        }
        seatHoldRepository.getHeldStatus(seatIds).forAll { it shouldBe false }

        // then 4-3 - 포인트 차감 + 충전/사용 이력
        springPointJpa.findByMemberId(memberId).get().point shouldBe CHARGE_AMOUNT - TOTAL_PRICE
        pointHistoryTypesOf(memberId) shouldContainExactly
            listOf(PointHistoryType.CHARGE.name, PointHistoryType.USE.name)

        // then 4-4 - 결제 레코드 저장
        val payment = springPaymentJpa.findByReservationId(reservationId).orElse(null).shouldNotBeNull()
        payment.price shouldBe TOTAL_PRICE
        payment.status shouldBe PaymentStatus.PAID.name

        // then 4-5 - 결제 완료 후 활성 토큰 반납 → 같은 토큰으로 다시 들어올 수 없다
        activeQueueRepository.findActive(scheduleId, uuid).shouldBeNull()
        shouldThrow<QueueTokenInvalidException> { queueFacade.validateToken(uuid, scheduleId, token) }
    }

    @Test
    fun `대기열 정원이 가득 차면 토큰 없이 대기 순번을 받고 활성 인원은 정원을 넘지 않는다`() {
        // given - 다른 사용자들로 활성 정원을 가득 채운다
        val member = fixture.createMember()
        val (scheduleId, _) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)
        val capacity = queueProperties.capacity

        (1..capacity).forEach { activeQueueRepository.saveActive(scheduleId, "other-user-$it") }
        activeQueueRepository.countActive(scheduleId) shouldBe capacity

        // when - 정원이 찬 상태에서 토큰 발급 요청
        val response = queueFacade.issueToken(member.uuid, scheduleId)

        // then 1 - 활성 토큰 대신 대기 상태와 순번(1번)을 받는다
        response.status shouldBe AdmissionStatus.WAITING.name
        response.token.shouldBeNull()
        response.rank shouldBe FIRST_RANK

        // then 2 - Redis 상태: 대기열에 등록되고, 활성 인원은 정원 그대로
        waitingQueueRepository.isWaiting(scheduleId, member.uuid) shouldBe true
        activeQueueRepository.findActive(scheduleId, member.uuid).shouldBeNull()
        activeQueueRepository.countActive(scheduleId) shouldBe capacity
    }

    @Test
    fun `다른 회원의 예약은 결제할 수 없고 좌석과 양쪽 포인트가 그대로 남는다`() {
        // given - 예약자는 좌석을 HOLD 했고, 다른 회원도 토큰과 포인트를 가지고 있다
        val owner = prepareHeldReservation()
        val other = fixture.createMember()
        val otherId = other.id.shouldNotBeNull()
        pointFacade.chargePoint(other.uuid, ChargePointRequest(CHARGE_AMOUNT))
        queueFacade.issueToken(other.uuid, owner.scheduleId).token.shouldNotBeNull()

        // when - 다른 회원이 예약자의 예약 ID 로 결제 시도
        shouldThrow<ReservationNotFoundException> {
            paymentFacade.processPayment(other.uuid, owner.paymentRequest())
        }

        // then 1 - 결제 레코드 없음, 좌석은 예약자의 HOLD 그대로
        springPaymentJpa.findByReservationId(owner.reservationId).orElse(null).shouldBeNull()
        springSeatJpa.findAllById(owner.seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe owner.reservationId
        }
        seatHoldRepository.getHeldStatus(owner.seatIds).forAll { it shouldBe true }

        // then 2 - 두 회원 모두 포인트 불변, 사용 이력 없음
        springPointJpa.findByMemberId(owner.memberId).get().point shouldBe CHARGE_AMOUNT
        springPointJpa.findByMemberId(otherId).get().point shouldBe CHARGE_AMOUNT
        pointHistoryTypesOf(owner.memberId) shouldContainExactly listOf(PointHistoryType.CHARGE.name)
        pointHistoryTypesOf(otherId) shouldContainExactly listOf(PointHistoryType.CHARGE.name)

        // then 3 - 예약자는 아직 결제할 수 있다 (활성 토큰 유지)
        activeQueueRepository.findActive(owner.scheduleId, owner.uuid).shouldNotBeNull()
    }

    @Test
    fun `같은 예약을 두 번 결제하면 두 번째는 실패하고 포인트는 한 번만 차감된다`() {
        // given - 첫 번째 결제 완료
        val ctx = prepareHeldReservation()
        paymentFacade.processPayment(ctx.uuid, ctx.paymentRequest())

        // when - 같은 예약으로 다시 결제 (재시도·더블 클릭 상황)
        shouldThrow<DuplicatePaymentException> {
            paymentFacade.processPayment(ctx.uuid, ctx.paymentRequest())
        }

        // then - 포인트 1회 차감, USE 이력 1건, 결제 레코드 1건, 좌석 확정 유지
        springPointJpa.findByMemberId(ctx.memberId).get().point shouldBe CHARGE_AMOUNT - TOTAL_PRICE
        pointHistoryTypesOf(ctx.memberId) shouldContainExactly
            listOf(PointHistoryType.CHARGE.name, PointHistoryType.USE.name)
        springPaymentJpa.findAll() shouldHaveSize 1 // 테스트마다 DB 를 비우므로 전체 = 이 예약의 결제
        springSeatJpa.findAllById(ctx.seatIds).forAll { it.status shouldBe SeatStatus.RESERVED.name }
    }

    /** 회원 생성 → 포인트 충전 → 토큰 발급·검증 → 좌석 예약(HOLD) 까지 진행한다 */
    private fun prepareHeldReservation(): HeldReservation {
        val member = fixture.createMember()
        val memberId = member.id.shouldNotBeNull()
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)

        pointFacade.chargePoint(member.uuid, ChargePointRequest(CHARGE_AMOUNT))
        val token = queueFacade.issueToken(member.uuid, scheduleId).token.shouldNotBeNull()
        queueFacade.validateToken(member.uuid, scheduleId, token)

        val reservationId = reservationFacade
            .reserveSeat(member.uuid, ReservationRequest(scheduleId, seatIds))
            .reservationId

        return HeldReservation(memberId, member.uuid, scheduleId, seatIds, reservationId)
    }

    private fun pointHistoryTypesOf(memberId: Long): List<String> =
        springPointHistoryJpa.findAll()
            .filter { it.memberId == memberId }
            .map { it.type }

    private data class HeldReservation(
        val memberId: Long,
        val uuid: String,
        val scheduleId: Long,
        val seatIds: List<Long>,
        val reservationId: Long
    ) {
        fun paymentRequest() =
            PaymentRequest(reservationId = reservationId, point = TOTAL_PRICE, scheduleId = scheduleId)
    }

    companion object {
        private const val SEAT_COUNT = 2
        private const val SEAT_PRICE = 50_000
        private const val TOTAL_PRICE = SEAT_PRICE * SEAT_COUNT
        private const val CHARGE_AMOUNT = 200_000
        private const val FIRST_RANK = 1L
    }
}
