package kr.hhplus.be.server.domains.seat.infrastructure.redis

import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate

@DisplayName("[통합] 좌석 홀드 Redis 저장소")
class SeatHoldRedisRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var seatHoldRepository: SeatHoldRepository
    @Autowired private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Test
    fun `만료 정리는 아직 만료 상태인 항목만 지우고, 그 사이 새로 잡힌 홀드는 남긴다`() {
        // given - 좌석 1은 만료된 홀드, 좌석 2는 만료 처리 대상으로 잡힌 뒤 다른 사용자가 다시 홀드함(미래 만료)
        seatHoldRepository.hold(listOf(seat(EXPIRED_SEAT_ID), seat(REHELD_SEAT_ID)))
        moveExpirationToPast(EXPIRED_SEAT_ID)

        // when - 만료 처리가 두 좌석 모두를 대상으로 정리 (해제 커밋 이후 늦게 실행되는 상황)
        seatHoldRepository.removeExpiredSeats(listOf(EXPIRED_SEAT_ID, REHELD_SEAT_ID))

        // then 1 - 만료된 좌석의 키와 만료 큐 항목은 함께 삭제
        seatHoldRepository.getHeldStatus(listOf(EXPIRED_SEAT_ID)).forAll { it shouldBe false }
        queueScoreOf(EXPIRED_SEAT_ID).shouldBeNull()

        // then 2 - 새로 잡힌 홀드는 키와 만료 큐 항목 모두 유지 → 결제 가능, 만료 시 정상 해제
        seatHoldRepository.getHeldStatus(listOf(REHELD_SEAT_ID)).forAll { it shouldBe true }
        queueScoreOf(REHELD_SEAT_ID).shouldNotBeNull()
        seatHoldRepository.isAllHoldActive(listOf(REHELD_SEAT_ID)) shouldBe true
    }

    @Test
    fun `결제 후 정리는 키와 만료 큐 항목을 함께 지운다`() {
        // given
        seatHoldRepository.hold(listOf(seat(EXPIRED_SEAT_ID), seat(REHELD_SEAT_ID)))

        // when
        seatHoldRepository.removeSeats(listOf(EXPIRED_SEAT_ID, REHELD_SEAT_ID))

        // then
        seatHoldRepository.getHeldStatus(listOf(EXPIRED_SEAT_ID, REHELD_SEAT_ID)).forAll { it shouldBe false }
        queueScoreOf(EXPIRED_SEAT_ID).shouldBeNull()
        queueScoreOf(REHELD_SEAT_ID).shouldBeNull()
    }

    private fun seat(id: Long) = Seat(id, SCHEDULE_ID, "A$id", SeatStatus.HOLD.name, PRICE)

    private fun moveExpirationToPast(seatId: Long) {
        redisTemplate.opsForZSet().add(QUEUE_KEY, seatId, (System.currentTimeMillis() - EXPIRED_AGO_MILLIS).toDouble())
    }

    private fun queueScoreOf(seatId: Long): Double? = redisTemplate.opsForZSet().score(QUEUE_KEY, seatId)

    companion object {
        private const val EXPIRED_SEAT_ID = 1L
        private const val REHELD_SEAT_ID = 2L
        private const val SCHEDULE_ID = 1L
        private const val PRICE = 50_000
        private const val QUEUE_KEY = "seat_delay_queue"
        private const val EXPIRED_AGO_MILLIS = 1_000L
    }
}
