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
 * 쓰기 순서는 [좌석 RESERVED(DB) → Redis 홀드 제거 → 포인트 차감 → USE 이력 → 결제 정보 조회 → 결제 저장] 이다.
 *
 * - 정상적인 입력 검증(중복 결제·금액 불일치·포인트 부족·홀드 만료)은 모두 첫 쓰기 "이전"에 일어난다.
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

        // then 4 - 토큰 반납은 process 성공 이후에만 실행되므로 활성 토큰 유지
        activeQueueRepository.findActive(ctx.scheduleId, ctx.uuid).shouldNotBeNull()
    }

    /**
     * 알려진 버그 재현 (프로덕션 코드 미수정)
     *
     * Redis 홀드 제거(confirmReservation)가 DB 트랜잭션 안에서 "즉시" 실행되므로
     * 이후 단계에서 예외가 나 DB 가 롤백돼도 Redis 는 되돌아가지 않는다.
     * 결과적으로 DB 는 HOLD 인데 seat_delay_queue 에서 빠져 만료 스케줄러가 이 좌석을 영영 풀지 못한다.
     *
     * 수정되면 이 테스트는 실패한다 → 기대값을 "홀드 유지(true / score 존재)"로 뒤집어 위 롤백 테스트에 합칠 것.
     */
    @Test
    fun `알려진 버그 - 쓰기 후 실패하면 Redis 좌석 홀드는 롤백되지 않아 DB 좌석 상태와 어긋난다`() {
        // given
        val ctx = prepareHeldReservation(chargeAmount = CHARGE_AMOUNT)
        detachConcertFromSchedule(ctx.scheduleId)

        // when
        shouldThrow<PaymentInfoNotFoundException> {
            paymentFacade.processPayment(ctx.uuid, ctx.paymentRequest())
        }

        // then 1 - DB 는 롤백되어 여전히 HOLD
        springSeatJpa.findAllById(ctx.seatIds).forAll { it.status shouldBe SeatStatus.HOLD.name }

        // then 2 - 그러나 Redis 홀드 키와 만료 큐(ZSET) 항목은 이미 삭제됨 (불일치)
        seatHoldRepository.getHeldStatus(ctx.seatIds).forAll { it shouldBe false }
        ctx.seatIds.forAll { seatDelayQueueScoreOf(it).shouldBeNull() }
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
        private const val DETACH_CONCERT_SQL = "UPDATE schedules SET concert_id = NULL WHERE schedule_id = ?"
    }
}
