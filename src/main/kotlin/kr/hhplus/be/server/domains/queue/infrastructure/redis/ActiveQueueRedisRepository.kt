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
        private const val KEY_PATTERN = "queue:schedule:%d:active:uuid:%s"
        private const val ZSET_KEY_PATTERN = "queue:schedule:%d:active"
        private const val SCHEDULE_SET_KEY = "queue:schedule:active"
    }

    override fun countActive(scheduleId: Long): Long {
        val now = System.currentTimeMillis()

        // 남은 활성 인원수 반환
        return redisTemplate.opsForZSet().count(activeKey(scheduleId), now.toDouble(), Double.MAX_VALUE) ?: 0L
    }

    /**
     * 활성 토큰 생성
     */
    override fun saveActive(scheduleId: Long, uuid: String): String {
        val key = activeUuidKey(scheduleId, uuid)
        val expirationTime = System.currentTimeMillis() + queueProperties.bookingTokenTtlMs

        // 토큰 생성
        val token = UUID.randomUUID().toString()

        // ZSET에 token 추가
        redisTemplate.opsForSet().add(SCHEDULE_SET_KEY, scheduleId.toString())
        redisTemplate.opsForZSet().add(activeKey(scheduleId), token, expirationTime.toDouble())

        // uuid -> token 매핑 저장
        redisTemplate.opsForValue().set(key, token, queueProperties.bookingTokenTtlMs, TimeUnit.MILLISECONDS)

        return token
    }

    override fun findActive(scheduleId: Long, uuid: String): String? =
        redisTemplate.opsForValue().get(activeUuidKey(scheduleId, uuid)) as? String

    override fun getRemainingSeconds(scheduleId: Long, uuid: String): Long {
        val token = findActive(scheduleId, uuid) ?: return 0L
        val expirationTime = redisTemplate.opsForZSet().score(activeKey(scheduleId), token)
            ?: return 0L

        return ((expirationTime - System.currentTimeMillis()) / 1000).toLong()
    }

    override fun removeExpiredActive(scheduleId: Long) {
        val key = activeKey(scheduleId)
        val now = System.currentTimeMillis().toDouble()

        redisTemplate.opsForZSet().removeRangeByScore(key, 0.0, now)
        removeScheduleIdIfEmpty(scheduleId, key)
    }

    override fun findScheduleIds(): List<Long> {
        return redisTemplate.opsForSet().members(SCHEDULE_SET_KEY)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
    }

    override fun isValidToken(scheduleId: Long, token: String): Boolean {
        val score = redisTemplate.opsForZSet().score(activeKey(scheduleId), token) ?: return false
        return score > System.currentTimeMillis()
    }

    private fun activeKey(scheduleId: Long): String =
        String.format(ZSET_KEY_PATTERN, scheduleId)

    private fun activeUuidKey(scheduleId: Long, uuid: String): String =
        String.format(KEY_PATTERN, scheduleId, uuid)

    private fun removeScheduleIdIfEmpty(scheduleId: Long, key: String) {
        val size = redisTemplate.opsForZSet().size(key) ?: 0L
        if (size == 0L) {
            redisTemplate.opsForSet().remove(SCHEDULE_SET_KEY, scheduleId.toString())
        }
    }
}
