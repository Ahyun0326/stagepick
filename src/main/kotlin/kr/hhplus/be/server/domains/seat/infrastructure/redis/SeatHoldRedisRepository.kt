package kr.hhplus.be.server.domains.seat.infrastructure.redis

import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
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

        // 키 삭제와 만료 큐 제거를 한 번에 실행 (중간 실패로 TTL 없는 키만 남는 것을 방지)
        // ARGV = 좌석 ID 목록
        private val REMOVE_SCRIPT = DefaultRedisScript(
            """
            for i = 1, #ARGV do
                redis.call('ZREM', KEYS[1], ARGV[i])
                redis.call('DEL', KEYS[i + 1])
            end
            return #ARGV
            """.trimIndent(),
            Long::class.java
        )

        // 만료 시각이 지금 이하인 항목만 제거 (Compare-And-Delete) → 새로 잡힌 홀드는 보존
        // ARGV[1] = 현재 시각(ms), ARGV[2..] = 좌석 ID 목록
        private val REMOVE_EXPIRED_SCRIPT = DefaultRedisScript(
            """
            local removed = 0
            for i = 2, #ARGV do
                local expiredAt = redis.call('ZSCORE', KEYS[1], ARGV[i])
                if expiredAt and tonumber(expiredAt) <= tonumber(ARGV[1]) then
                    redis.call('ZREM', KEYS[1], ARGV[i])
                    redis.call('DEL', KEYS[i])
                    removed = removed + 1
                end
            end
            return removed
            """.trimIndent(),
            Long::class.java
        )
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

    override fun isAllHoldActive(seatIds: List<Long>): Boolean {
        val now = System.currentTimeMillis().toDouble()

        // 홀드 키에는 TTL 이 없으므로 만료 여부는 만료 큐(score = 만료 시각)로 판단
        return seatIds.all { seatId ->
            val expiredAt = redisTemplate.opsForZSet().score(ZSET_KEY, seatId)
            expiredAt != null && expiredAt > now
        }
    }

    override fun removeSeats(seatIds: List<Long>) {
        if (seatIds.isEmpty()) return
        redisTemplate.execute(REMOVE_SCRIPT, keysOf(seatIds), *seatIds.toTypedArray())
    }

    override fun removeExpiredSeats(seatIds: List<Long>) {
        if (seatIds.isEmpty()) return
        val now = System.currentTimeMillis()
        redisTemplate.execute(REMOVE_EXPIRED_SCRIPT, keysOf(seatIds), now, *seatIds.toTypedArray())
    }

    /** KEYS[1] = 만료 큐, KEYS[2..] = 좌석별 홀드 키 (ARGV 의 좌석 순서와 같음) */
    private fun keysOf(seatIds: List<Long>): List<String> =
        listOf(ZSET_KEY) + seatIds.map { String.format(KEY_PATTERN, it) }
}