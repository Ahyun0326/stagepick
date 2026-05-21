package kr.hhplus.be.server.domains.queue.infrastructure.redis

import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class WaitingQueueRedisRepository(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val queueProperties: QueueProperties
) : WaitingQueueRepository {

    companion object {
        private const val ZSET_KEY_PATTERN = "queue:schedule:%d:waiting"
        private const val HEARTBEAT_KEY_PATTERN = "queue:schedule:%d:waiting:heartbeat:%s"
        private const val SCHEDULE_SET_KEY = "queue:schedule:waiting"
        private const val HEARTBEAT_VALUE = "ALIVE"
    }

    override fun add(scheduleId: Long, uuid: String) {
        val joinedAtMillis = System.currentTimeMillis()
        redisTemplate.opsForSet().add(SCHEDULE_SET_KEY, scheduleId.toString())
        redisTemplate.opsForZSet().add(waitingKey(scheduleId), uuid, joinedAtMillis.toDouble())
        touchHeartbeat(scheduleId, uuid)
    }

    override fun getRank(scheduleId: Long, uuid: String): Long? =
        redisTemplate.opsForZSet().rank(waitingKey(scheduleId), uuid)?.let { it + 1 }

    // 빈 슬롯만큼 대기자를 활성 상태로 승격
    override fun popWaiting(scheduleId: Long, count: Long): List<String> {
        val key = waitingKey(scheduleId)
        val waitingUuids = redisTemplate.opsForZSet().popMin(key, count)
            ?.mapNotNull { it.value as? String }
            ?: emptyList()

        deleteHeartbeat(scheduleId, waitingUuids)
        removeScheduleIdIfEmpty(scheduleId, key)

        return waitingUuids
    }

    override fun isWaiting(scheduleId: Long, uuid: String): Boolean =
        redisTemplate.opsForZSet().score(waitingKey(scheduleId), uuid) != null

    override fun touchHeartbeat(scheduleId: Long, uuid: String) {
        redisTemplate.opsForValue().set(
            heartbeatKey(scheduleId, uuid),
            HEARTBEAT_VALUE,
            queueProperties.waitingHeartbeatTtlMs,
            TimeUnit.MILLISECONDS
        )
    }

    override fun removeInactiveWaiting(scheduleId: Long) {
        val key = waitingKey(scheduleId)
        val inactiveUuids = redisTemplate.opsForZSet()
            .range(key, 0, -1)
            ?.mapNotNull { it as? String }
            ?.filterNot { redisTemplate.hasKey(heartbeatKey(scheduleId, it)) == true }
            ?: emptyList()

        if (inactiveUuids.isNotEmpty()) {
            redisTemplate.opsForZSet().remove(key, *inactiveUuids.toTypedArray())
        }
        removeScheduleIdIfEmpty(scheduleId, key)
    }

    override fun findScheduleIds(): List<Long> {
        return redisTemplate.opsForSet().members(SCHEDULE_SET_KEY)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
    }

    private fun waitingKey(scheduleId: Long): String =
        String.format(ZSET_KEY_PATTERN, scheduleId)

    private fun heartbeatKey(scheduleId: Long, uuid: String): String =
        String.format(HEARTBEAT_KEY_PATTERN, scheduleId, uuid)

    private fun deleteHeartbeat(scheduleId: Long, uuids: List<String>) {
        if (uuids.isNotEmpty()) {
            redisTemplate.delete(uuids.map { heartbeatKey(scheduleId, it) })
        }
    }

    private fun removeScheduleIdIfEmpty(scheduleId: Long, key: String) {
        val size = redisTemplate.opsForZSet().size(key) ?: 0L
        if (size == 0L) {
            redisTemplate.opsForSet().remove(SCHEDULE_SET_KEY, scheduleId.toString())
        }
    }
}
