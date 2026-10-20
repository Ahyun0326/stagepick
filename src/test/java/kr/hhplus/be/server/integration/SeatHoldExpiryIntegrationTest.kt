package kr.hhplus.be.server.integration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.common.exception.ReservationSeatExpiredException
import kr.hhplus.be.server.common.exception.SeatUnavailableException
import kr.hhplus.be.server.domains.member.domain.model.Member
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.facade.PaymentFacade
import kr.hhplus.be.server.domains.payment.infrastructure.persistence.SpringPaymentJpa
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.facade.PointFacade
import kr.hhplus.be.server.domains.point.infrastructure.persistence.SpringPointJpa
import kr.hhplus.be.server.domains.queue.application.facade.QueueFacade
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.facade.ReservationFacade
import kr.hhplus.be.server.domains.seat.application.facade.SeatFacade
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SpringSeatJpa
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate

/**
 * 필수 통합 테스트 ② - 좌석 홀드 만료 시간이 지나면 다른 사용자가 다시 예약할 수 있다
 *
 * integration 프로파일은 스케줄러가 꺼져 있으므로(app.scheduler.enabled 미설정)
 * 스케줄러가 호출하는 SeatFacade.expireSeatHold() 를 직접 실행한다.
 * 5분을 기다리는 대신 만료 큐(seat_delay_queue)의 만료 시각(score)을 과거로 옮겨 "시간이 지난" 상태를 만든다.
 */
@DisplayName("[필수 통합] 좌석 홀드 만료 후 재예약")
class SeatHoldExpiryIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture

    @Autowired private lateinit var queueFacade: QueueFacade
    @Autowired private lateinit var pointFacade: PointFacade
    @Autowired private lateinit var reservationFacade: ReservationFacade
    @Autowired private lateinit var paymentFacade: PaymentFacade
    @Autowired private lateinit var seatFacade: SeatFacade

    @Autowired private lateinit var springSeatJpa: SpringSeatJpa
    @Autowired private lateinit var springPointJpa: SpringPointJpa
    @Autowired private lateinit var springPaymentJpa: SpringPaymentJpa
    @Autowired private lateinit var seatHoldRepository: SeatHoldRepository
    @Autowired private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Test
    fun `홀드 만료 시간이 지나면 좌석이 풀리고 다른 사용자가 다시 예약할 수 있다`() {
        // given - A 가 좌석을 홀드했고, B 도 같은 회차의 토큰을 받았다
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)
        val memberA = fixture.createMember()
        val memberB = fixture.createMember()
        val reservationA = reserveWithToken(memberA, scheduleId, seatIds)
        issueAndValidateToken(memberB, scheduleId)

        // 만료 전에는 B 가 같은 좌석을 예약할 수 없다 (홀드가 실제로 막고 있음을 먼저 확인)
        shouldThrow<SeatUnavailableException> {
            reservationFacade.reserveSeat(memberB.uuid, ReservationRequest(scheduleId, seatIds))
        }

        // when 1 - 홀드 시간이 지나고 만료 스케줄러가 실행된다
        seatIds.forEach { moveExpirationToPast(it) }
        seatFacade.expireSeatHold()

        // then 1 - DB 좌석은 AVAILABLE + 예약 연결 해제, Redis 홀드 키·만료 큐 항목도 제거
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.AVAILABLE.name
            it.reservationId.shouldBeNull()
        }
        seatHoldRepository.getHeldStatus(seatIds).forAll { it shouldBe false }
        seatIds.forAll { queueScoreOf(it).shouldBeNull() }

        // when 2 - B 가 같은 좌석을 다시 예약한다
        val reservationB = reservationFacade
            .reserveSeat(memberB.uuid, ReservationRequest(scheduleId, seatIds))
            .reservationId

        // then 2 - 좌석은 B 의 예약으로 다시 HOLD, 새 홀드가 Redis 에 등록된다
        reservationB shouldNotBe reservationA
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe reservationB
        }
        seatHoldRepository.getHeldStatus(seatIds).forAll { it shouldBe true }
        seatHoldRepository.isAllHoldActive(seatIds) shouldBe true
    }

    @Test
    fun `만료로 좌석을 잃은 사용자의 결제는 거절되고 포인트는 그대로다`() {
        // given - A 가 홀드 후 결제하지 않은 채 만료되었다
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)
        val memberA = fixture.createMember()
        val memberAId = memberA.id.shouldNotBeNull()
        pointFacade.chargePoint(memberA.uuid, ChargePointRequest(CHARGE_AMOUNT))
        val reservationA = reserveWithToken(memberA, scheduleId, seatIds)

        seatIds.forEach { moveExpirationToPast(it) }
        seatFacade.expireSeatHold()

        // when - 뒤늦게 결제를 시도한다
        shouldThrow<ReservationSeatExpiredException> {
            paymentFacade.processPayment(
                memberA.uuid,
                PaymentRequest(reservationId = reservationA, point = TOTAL_PRICE, scheduleId = scheduleId)
            )
        }

        // then - 결제 레코드 없음, 포인트 그대로, 좌석은 AVAILABLE 유지
        springPaymentJpa.findByReservationId(reservationA).orElse(null).shouldBeNull()
        springPointJpa.findByMemberId(memberAId).get().point shouldBe CHARGE_AMOUNT
        springSeatJpa.findAllById(seatIds).forAll { it.status shouldBe SeatStatus.AVAILABLE.name }
    }

    /** 토큰 발급·검증 후 좌석을 예약하고 예약 ID 를 반환한다 */
    private fun reserveWithToken(member: Member, scheduleId: Long, seatIds: List<Long>): Long {
        issueAndValidateToken(member, scheduleId)
        return reservationFacade.reserveSeat(member.uuid, ReservationRequest(scheduleId, seatIds)).reservationId
    }

    /** HTTP 요청에서는 QueueTokenInterceptor 가 수행하는 토큰 검증을 명시적으로 호출한다 */
    private fun issueAndValidateToken(member: Member, scheduleId: Long) {
        val token = queueFacade.issueToken(member.uuid, scheduleId).token.shouldNotBeNull()
        queueFacade.validateToken(member.uuid, scheduleId, token)
    }

    private fun moveExpirationToPast(seatId: Long) {
        redisTemplate.opsForZSet().add(QUEUE_KEY, seatId, (System.currentTimeMillis() - EXPIRED_AGO_MILLIS).toDouble())
    }

    private fun queueScoreOf(seatId: Long): Double? = redisTemplate.opsForZSet().score(QUEUE_KEY, seatId)

    companion object {
        private const val SEAT_COUNT = 2
        private const val SEAT_PRICE = 50_000
        private const val TOTAL_PRICE = SEAT_PRICE * SEAT_COUNT
        private const val CHARGE_AMOUNT = 200_000
        private const val QUEUE_KEY = "seat_delay_queue"
        private const val EXPIRED_AGO_MILLIS = 1_000L
    }
}
