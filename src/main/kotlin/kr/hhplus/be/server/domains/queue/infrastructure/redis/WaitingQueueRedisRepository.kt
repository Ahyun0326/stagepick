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
        private const val HEARTBEAT_VALUE = "ALIVE"
    }

    override fun add(scheduleId: Long, uuid: String) {
        val joinedAtMillis = System.currentTimeMillis()
        redisTemplate.opsForSet().add(QueueRedisKeys.WAITING_SCHEDULE_SET_KEY, scheduleId.toString())
        redisTemplate.opsForZSet().add(waitingKey(scheduleId), uuid, joinedAtMillis.toDouble())
        touchHeartbeat(scheduleId, uuid)
    }

    override fun getRank(scheduleId: Long, uuid: String): Long? =
        redisTemplate.opsForZSet().rank(waitingKey(scheduleId), uuid)?.let { it + 1 }

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
        return redisTemplate.opsForSet().members(QueueRedisKeys.WAITING_SCHEDULE_SET_KEY)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
    }

    private fun waitingKey(scheduleId: Long): String = QueueRedisKeys.waitingKey(scheduleId)

    private fun heartbeatKey(scheduleId: Long, uuid: String): String =
        QueueRedisKeys.waitingHeartbeatKey(scheduleId, uuid)

    private fun removeScheduleIdIfEmpty(scheduleId: Long, key: String) {
        val size = redisTemplate.opsForZSet().size(key) ?: 0L
        if (size == 0L) {
            redisTemplate.opsForSet().remove(QueueRedisKeys.WAITING_SCHEDULE_SET_KEY, scheduleId.toString())
        }
    }
}
