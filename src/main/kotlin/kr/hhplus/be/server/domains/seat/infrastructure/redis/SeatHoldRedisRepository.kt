package kr.hhplus.be.server.domains.seat.infrastructure.redis

import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

@Repository
class SeatHoldRedisRepository(
    private val redisTemplate: RedisTemplate<String, Any>
) : SeatHoldRepository {

    companion object {
        private const val KEY_PATTERN = "seat:hold:%d"
        private const val VALUE = "HELD"
        private const val ZSET_KEY = "seat_delay_queue"
        private const val TTL_SECONDS = 5 * 60
    }

    override fun hold(seats: List<Seat>) {
        val now = System.currentTimeMillis()
        val expiredAt = now + TTL_SECONDS * 1000

        seats.forEach {
            val key = String.format(KEY_PATTERN, it.id)

            redisTemplate.opsForValue().set(key, VALUE)
            redisTemplate.opsForZSet().add(ZSET_KEY, it.id, expiredAt.toDouble())
        }
    }

    override fun getHeldStatus(seatIds: List<Long>): List<Boolean> {
        val keys = seatIds.map { String.format(KEY_PATTERN, it) }
        val values = redisTemplate.opsForValue().multiGet(keys)

        return values?.map { it != null } ?: emptyList()
    }

    override fun getExpiredSeats(): List<Long> {
        val now = System.currentTimeMillis().toDouble()

        val expiredSeatIds = redisTemplate.opsForZSet()
            .rangeByScore(ZSET_KEY, 0.0, now, 0, 100)
            ?.map { it.toString().toLong() }
            ?: emptyList()

        return expiredSeatIds
    }

    override fun removeSeats(expiredSeatIds: List<Long>) {
        redisTemplate.opsForZSet().remove(ZSET_KEY, expiredSeatIds)

        // Redis Key 명시적 삭제
        val keys = expiredSeatIds.map { String.format(KEY_PATTERN, it) }
        redisTemplate.delete(keys)

        redisTemplate.opsForZSet().remove(ZSET_KEY, expiredSeatIds)
    }
}