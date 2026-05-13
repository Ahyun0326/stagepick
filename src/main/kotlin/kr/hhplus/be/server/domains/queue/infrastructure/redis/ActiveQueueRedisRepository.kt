package kr.hhplus.be.server.domains.queue.infrastructure.redis

import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.TimeUnit

@Repository
class ActiveQueueRedisRepository(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val queueProperties: QueueProperties
) : ActiveQueueRepository {

    companion object {
        private const val KEY_PATTERN = "queue:active:uuid:%s"
        private const val ZSET_KEY = "queue:active"
        private const val WAITING_KEY = "queue:waiting"
    }

    override fun countActive(): Long {
        val now = System.currentTimeMillis()

        // 남은 활성 인원수 반환
        return redisTemplate.opsForZSet().count(ZSET_KEY, now.toDouble(), Double.MAX_VALUE) ?: 0L
    }

    /**
     * 활성 토큰 생성
     */
    override fun saveActive(uuid: String): String {
        val key = String.format(KEY_PATTERN, uuid)
        val expirationTime = System.currentTimeMillis() + queueProperties.bookingTokenTtlMs

        // 토큰 생성
        val token = UUID.randomUUID().toString()

        // ZSET에 token 추가
        redisTemplate.opsForZSet().add(ZSET_KEY, token, expirationTime.toDouble())

        // uuid -> token 매핑 저장
        redisTemplate.opsForValue().set(key, token, queueProperties.bookingTokenTtlMs, TimeUnit.MILLISECONDS)

        // 대기열에서 제거
        redisTemplate.opsForZSet().remove(WAITING_KEY, uuid)

        return token
    }

    override fun findActive(uuid: String): String? =
        redisTemplate.opsForValue().get(String.format(KEY_PATTERN, uuid)) as? String

    override fun getRemainingSeconds(uuid: String): Long {
        val token = findActive(uuid) ?: return 0L
        val expirationTime = redisTemplate.opsForZSet().score(ZSET_KEY, token)
            ?: return 0L

        return ((expirationTime - System.currentTimeMillis()) / 1000).toLong()
    }

    override fun removeExpiredActive() {
        val now = System.currentTimeMillis().toDouble()
        redisTemplate.opsForZSet().removeRangeByScore(ZSET_KEY, 0.0, now)
    }

    override fun isValidToken(token: String): Boolean {
        val score = redisTemplate.opsForZSet().score(ZSET_KEY, token) ?: return false
        return score > System.currentTimeMillis()
    }
}
