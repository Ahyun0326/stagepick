package kr.hhplus.be.server.domains.queue.infrastructure.redis

import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
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

        // 정원 확인(ZCOUNT)과 등록(SADD+ZADD+SET)을 한 번에 실행 → countActive 후 saveActive를
        // 따로 호출하던 check-then-act 경합(정원 초과 발급)을 막는다.
        // KEYS[1] = 스케줄 활성 ZSET, KEYS[2] = uuid -> token 매핑 key, KEYS[3] = 활성 스케줄 집합
        // ARGV[1] = 현재 시각(ms), ARGV[2] = 정원, ARGV[3] = 새로 발급할 토큰,
        // ARGV[4] = 만료 시각(ms), ARGV[5] = TTL(ms), ARGV[6] = scheduleId(문자열)
        // 반환: 1 = 활성 보장(기존 토큰 유지 또는 신규 발급), 0 = 정원 초과로 미발급
        // 같은 uuid로 동시에 여러 번 호출돼도 Redis가 EVAL을 한 번에 하나씩만 실행하므로,
        // 먼저 실행된 호출이 심어둔 uuid 매핑을 뒤이은 호출이 그대로 재사용해 토큰이 중복 집계되지 않는다.
        private val TRY_ISSUE_ACTIVE_SCRIPT = DefaultRedisScript(
            """
            local existingToken = redis.call('GET', KEYS[2])
            if existingToken then
                local score = redis.call('ZSCORE', KEYS[1], existingToken)
                if score and tonumber(score) > tonumber(ARGV[1]) then
                    return 1
                end
            end

            local activeCount = redis.call('ZCOUNT', KEYS[1], ARGV[1], '+inf')
            if tonumber(activeCount) < tonumber(ARGV[2]) then
                redis.call('SADD', KEYS[3], ARGV[6])
                redis.call('ZADD', KEYS[1], ARGV[4], ARGV[3])
                redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[5])
                return 1
            end

            return 0
            """.trimIndent(),
            Long::class.java
        )
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
        val expirationTime = System.currentTimeMillis() + queueProperties.activeTokenTtlMs

        // 토큰 생성
        val token = UUID.randomUUID().toString()

        // ZSET에 token 추가
        redisTemplate.opsForSet().add(SCHEDULE_SET_KEY, scheduleId.toString())
        redisTemplate.opsForZSet().add(activeKey(scheduleId), token, expirationTime.toDouble())

        // uuid -> token 매핑 저장
        redisTemplate.opsForValue().set(key, token, queueProperties.activeTokenTtlMs, TimeUnit.MILLISECONDS)

        return token
    }

    override fun tryIssueActive(scheduleId: Long, uuid: String, capacity: Long): Boolean {
        val now = System.currentTimeMillis()
        val token = UUID.randomUUID().toString()
        val expirationTime = now + queueProperties.activeTokenTtlMs

        val admitted = redisTemplate.execute(
            TRY_ISSUE_ACTIVE_SCRIPT,
            listOf(activeKey(scheduleId), activeUuidKey(scheduleId, uuid), SCHEDULE_SET_KEY),
            now,
            capacity,
            token,
            expirationTime,
            queueProperties.activeTokenTtlMs,
            scheduleId.toString()
        )

        return admitted == 1L
    }

    override fun findActive(scheduleId: Long, uuid: String): String? =
        redisTemplate.opsForValue().get(activeUuidKey(scheduleId, uuid)) as? String

    override fun getRemainingSeconds(scheduleId: Long, uuid: String): Long {
        val token = findActive(scheduleId, uuid) ?: return 0L
        val expirationTime = redisTemplate.opsForZSet().score(activeKey(scheduleId), token)
            ?: return 0L

        return ((expirationTime - System.currentTimeMillis()) / 1000).toLong()
    }

    override fun removeActive(scheduleId: Long, uuid: String) {
        val key = activeKey(scheduleId)
        val uuidKey = activeUuidKey(scheduleId, uuid)
        val token = redisTemplate.opsForValue().get(uuidKey) as? String ?: return

        redisTemplate.opsForZSet().remove(key, token)
        redisTemplate.delete(uuidKey)
        removeScheduleIdIfEmpty(scheduleId, key)
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
