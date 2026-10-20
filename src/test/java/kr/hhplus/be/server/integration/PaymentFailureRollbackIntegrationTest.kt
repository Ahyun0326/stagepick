package kr.hhplus.be.server.integration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.common.exception.InsufficientPointException
import kr.hhplus.be.server.common.exception.PaymentInfoNotFoundException
import kr.hhplus.be.server.common.exception.ReservationSeatExpiredException
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.facade.PaymentFacade
import kr.hhplus.be.server.domains.payment.infrastructure.persistence.SpringPaymentJpa
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.facade.PointFacade
import kr.hhplus.be.server.domains.point.domain.model.PointHistoryType
import kr.hhplus.be.server.domains.point.infrastructure.persistence.SpringPointHistoryJpa
import kr.hhplus.be.server.domains.point.infrastructure.persistence.SpringPointJpa
import kr.hhplus.be.server.domains.queue.application.facade.QueueFacade
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
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
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 결제 실패 시 데이터 정합성(트랜잭션 롤백) 통합 테스트
 *
 * ProcessPaymentService.process 는 분산락 안에서 REQUIRES_NEW 트랜잭션으로 실행되며,
 * 쓰기 순서는 [좌석 RESERVED(DB) → 포인트 차감 → USE 이력 → 결제 정보 조회 → 결제 저장] 이고,
 * Redis 홀드 제거는 트랜잭션 커밋 "이후"에만 실행된다.
 *
 * - 정상적인 입력 검증(중복 결제·금액 불일치·포인트 부족·홀드 만료)은 모두 첫 쓰기 "이전"에 일어난다.
 * - 만료 처리는 HOLD 인 좌석만 해제하므로, 결제된 좌석에 Redis 항목이 남아 있어도 되돌리지 않는다.
 * - 첫 쓰기 "이후"에 실패할 수 있는 지점은 결제 정보 조회(getWithDetailsById) 뿐이며,
 *   좌석 → 회차 → 콘서트 inner join 이 끊기면 PaymentInfoNotFoundException 이 발생한다.
 *   스키마상 schedules.concert_id 가 nullable 이므로, 실제 DB 데이터를 조작해 이 상태를 만든다 (빈 mock/spy 없음).
 */
@DisplayName("[통합] 결제 실패 시 데이터 정합성")
class PaymentFailureRollbackIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture

    @Autowired private lateinit var queueFacade: QueueFacade
    @Autowired private lateinit var pointFacade: PointFacade
    @Autowired private lateinit var reservationFacade: ReservationFacade
    @Autowired private lateinit var paymentFacade: PaymentFacade
    @Autowired private lateinit var seatFacade: SeatFacade

    @Autowired private lateinit var springSeatJpa: SpringSeatJpa
    @Autowired private lateinit var springPointJpa: SpringPointJpa
    @Autowired private lateinit var springPointHistoryJpa: SpringPointHistoryJpa
    @Autowired private lateinit var springPaymentJpa: SpringPaymentJpa
    @Autowired private lateinit var seatHoldRepository: SeatHoldRepository
    @Autowired private lateinit var activeQueueRepository: ActiveQueueRepository
    @Autowired private lateinit var redisTemplate: RedisTemplate<String, Any>
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `금액 검증을 통과한 뒤 결제 정보 조회에서 실패하면 좌석 확정과 포인트 차감이 모두 롤백된다`() {
        // given - 좌석 HOLD 상태의 예약 + 결제 정보 조회 join 을 끊는다 (concert_id = NULL)
        val ctx = prepareHeldReservation(chargeAmount = CHARGE_AMOUNT)
        detachConcertFromSchedule(ctx.scheduleId)

        // when - 좌석 RESERVED 변경·포인트 차감·USE 이력 저장이 끝난 "뒤" 예외 발생
        shouldThrow<PaymentInfoNotFoundException> {
            paymentFacade.processPayment(ctx.uuid, ctx.paymentRequest())
        }

        // then 1 - 포인트 잔액 불변, USE 이력 없음
        springPointJpa.findByMemberId(ctx.memberId).get().point shouldBe CHARGE_AMOUNT
        pointHistoryTypesOf(ctx.memberId) shouldContainExactly listOf(PointHistoryType.CHARGE.name)

        // then 2 - 결제 레코드 없음
        springPaymentJpa.findByReservationId(ctx.reservationId).orElse(null).shouldBeNull()

        // then 3 - 이미 실행된 bulk UPDATE(RESERVED)도 롤백되어 HOLD + reservationId 유지
        springSeatJpa.findAllById(ctx.seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe ctx.reservationId
        }

        // then 4 - Redis 홀드 키와 만료 큐(ZSET) 항목도 유지 (홀드 정리는 커밋 이후에만 실행)
        //          → DB(HOLD)와 Redis 가 일치하므로 만료 시 스케줄러가 정상 해제할 수 있다
        seatHoldRepository.getHeldStatus(ctx.seatIds).forAll { it shouldBe true }
        ctx.seatIds.forAll { seatDelayQueueScoreOf(it).shouldNotBeNull() }

        // then 5 - 토큰 반납은 process 성공 이후에만 실행되므로 활성 토큰 유지
        activeQueueRepository.findActive(ctx.scheduleId, ctx.uuid).shouldNotBeNull()
    }

    @Test
    fun `포인트가 부족하면 결제가 실패하고 아무 상태도 바뀌지 않는다`() {
        // given - 좌석 가격 합보다 적은 포인트
        val ctx = prepareHeldReservation(chargeAmount = INSUFFICIENT_CHARGE_AMOUNT)

        // when - 첫 쓰기 이전 검증 단계에서 실패
        shouldThrow<InsufficientPointException> {
            paymentFacade.processPayment(ctx.uuid, ctx.paymentRequest())
        }

        // then 1 - 포인트 잔액 불변, USE 이력 없음
        springPointJpa.findByMemberId(ctx.memberId).get().point shouldBe INSUFFICIENT_CHARGE_AMOUNT
        pointHistoryTypesOf(ctx.memberId) shouldContainExactly listOf(PointHistoryType.CHARGE.name)

        // then 2 - 결제 레코드 없음
        springPaymentJpa.findByReservationId(ctx.reservationId).orElse(null).shouldBeNull()

        // then 3 - 좌석 HOLD + reservationId 유지
        springSeatJpa.findAllById(ctx.seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe ctx.reservationId
        }

        // then 4 - Redis 홀드 키와 만료 큐(ZSET) 항목 유지 → 만료 시 스케줄러가 정상 해제 가능
        seatHoldRepository.getHeldStatus(ctx.seatIds).forAll { it shouldBe true }
        ctx.seatIds.forAll { seatDelayQueueScoreOf(it).shouldNotBeNull() }

        // then 5 - 활성 토큰 유지 (재시도 가능)
        activeQueueRepository.findActive(ctx.scheduleId, ctx.uuid).shouldNotBeNull()
    }

    @Test
    fun `홀드가 만료된 뒤 결제하면 거절되고 아무 상태도 바뀌지 않는다`() {
        // given - 홀드 만료 시각이 지났지만 스케줄러가 아직 해제하지 않은 상태 (만료 큐 score 를 과거로)
        val ctx = prepareHeldReservation(chargeAmount = CHARGE_AMOUNT)
        ctx.seatIds.forEach { expireHoldInQueue(it) }

        // when - 만료된 홀드로 결제 시도
        shouldThrow<ReservationSeatExpiredException> {
            paymentFacade.processPayment(ctx.uuid, ctx.paymentRequest())
        }

        // then - 첫 쓰기 이전에 거절되어 포인트·결제·좌석 상태 모두 그대로
        springPointJpa.findByMemberId(ctx.memberId).get().point shouldBe CHARGE_AMOUNT
        pointHistoryTypesOf(ctx.memberId) shouldContainExactly listOf(PointHistoryType.CHARGE.name)
        springPaymentJpa.findByReservationId(ctx.reservationId).orElse(null).shouldBeNull()
        springSeatJpa.findAllById(ctx.seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe ctx.reservationId
        }
    }

    @Test
    fun `결제가 끝난 좌석에 만료 항목이 남아 있어도 만료 처리는 좌석을 되돌리지 않는다`() {
        // given - 결제 완료 후, 커밋 이후 Redis 정리가 실패했거나 스케줄러가 경합한 상황을 재현
        //         (결제된 좌석의 홀드 키와 만료 큐 항목이 과거 만료 시각으로 남아 있음)
        val ctx = prepareHeldReservation(chargeAmount = CHARGE_AMOUNT)
        paymentFacade.processPayment(ctx.uuid, ctx.paymentRequest())
        ctx.seatIds.forEach { leaveStaleHold(it) }

        // when - 만료 스케줄러가 실행하는 처리
        seatFacade.expireSeatHold()

        // then 1 - 결제된 좌석은 RESERVED 와 예약 연결 유지 (HOLD 인 좌석만 해제)
        springSeatJpa.findAllById(ctx.seatIds).forAll {
            it.status shouldBe SeatStatus.RESERVED.name
            it.reservationId shouldBe ctx.reservationId
        }

        // then 2 - 남아 있던 Redis 항목은 정리되어 다음 실행에서 다시 잡히지 않는다
        seatHoldRepository.getHeldStatus(ctx.seatIds).forAll { it shouldBe false }
        ctx.seatIds.forAll { seatDelayQueueScoreOf(it).shouldBeNull() }
    }

    /** 회원 생성 → 포인트 충전 → 토큰 발급 → 좌석 예약(HOLD) 까지 진행한다 */
    private fun prepareHeldReservation(chargeAmount: Int): HeldReservation {
        val member = fixture.createMember()
        val memberId = member.id.shouldNotBeNull()
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)

        pointFacade.chargePoint(member.uuid, ChargePointRequest(chargeAmount))
        queueFacade.issueToken(member.uuid, scheduleId).token.shouldNotBeNull()

        val reservationId = reservationFacade
            .reserveSeat(member.uuid, ReservationRequest(scheduleId, seatIds))
            .reservationId

        return HeldReservation(memberId, member.uuid, scheduleId, seatIds, reservationId)
    }

    /** 회차의 콘서트 참조를 끊는다 - nullable FK 라서 DB 가 허용하는 실제 데이터 상태 */
    private fun detachConcertFromSchedule(scheduleId: Long) {
        jdbcTemplate.update(DETACH_CONCERT_SQL, scheduleId) shouldBe 1
    }

    private fun pointHistoryTypesOf(memberId: Long): List<String> =
        springPointHistoryJpa.findAll()
            .filter { it.memberId == memberId }
            .map { it.type }

    private fun seatDelayQueueScoreOf(seatId: Long): Double? =
        redisTemplate.opsForZSet().score(SEAT_DELAY_QUEUE_KEY, seatId)

    /** 만료 큐의 만료 시각(score)을 과거로 바꿔 "홀드 시간이 지난" 상태를 만든다 */
    private fun expireHoldInQueue(seatId: Long) {
        redisTemplate.opsForZSet().add(SEAT_DELAY_QUEUE_KEY, seatId, pastMillis())
    }

    /** 결제가 끝난 좌석에 홀드 키와 과거 만료 시각의 큐 항목을 다시 남긴다 */
    private fun leaveStaleHold(seatId: Long) {
        redisTemplate.opsForValue().set(String.format(SEAT_HOLD_KEY_PATTERN, seatId), SEAT_HOLD_VALUE)
        redisTemplate.opsForZSet().add(SEAT_DELAY_QUEUE_KEY, seatId, pastMillis())
    }

    private fun pastMillis(): Double = (System.currentTimeMillis() - EXPIRED_AGO_MILLIS).toDouble()

    private data class HeldReservation(
        val memberId: Long,
        val uuid: String,
        val scheduleId: Long,
        val seatIds: List<Long>,
        val reservationId: Long
    ) {
        // 요청 금액은 좌석 가격 합과 정확히 일치시켜 금액 불일치 검증은 통과시킨다
        fun paymentRequest() = PaymentRequest(
            reservationId = reservationId,
            point = SEAT_PRICE * SEAT_COUNT,
            scheduleId = scheduleId
        )
    }

    companion object {
        private const val SEAT_COUNT = 2
        private const val SEAT_PRICE = 50_000
        private const val CHARGE_AMOUNT = 200_000
        private const val INSUFFICIENT_CHARGE_AMOUNT = 30_000
        private const val SEAT_DELAY_QUEUE_KEY = "seat_delay_queue"
        private const val SEAT_HOLD_KEY_PATTERN = "seat:hold:%d"
        private const val SEAT_HOLD_VALUE = "HELD"
        private const val EXPIRED_AGO_MILLIS = 1_000L
        private const val DETACH_CONCERT_SQL = "UPDATE schedules SET concert_id = NULL WHERE schedule_id = ?"
    }
}
