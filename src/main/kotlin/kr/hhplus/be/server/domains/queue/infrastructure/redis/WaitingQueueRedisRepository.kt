package kr.hhplus.be.server.domains.queue.infrastructure.redis

import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

@Repository
class WaitingQueueRedisRepository(
    private val redisTemplate: RedisTemplate<String, Any>
) : WaitingQueueRepository {

    companion object {
        private const val ZSET_KEY = "queue:waiting"
        private const val TTL_MS = 10 * 60 * 1000L
    }

    override fun add(uuid: String) {
        val expirationTime = System.currentTimeMillis() + TTL_MS
        redisTemplate.opsForZSet().add(ZSET_KEY, uuid, expirationTime.toDouble())
    }

    override fun getRank(uuid: String): Long? =
        redisTemplate.opsForZSet().rank(ZSET_KEY, uuid)?.let { it + 1}

    // 빈 슬롯만큼 대기자를 활성 상태로 승격
    override fun popWaiting(count: Long): List<String> =
        redisTemplate.opsForZSet().popMin(ZSET_KEY, count)
            ?.mapNotNull { it.value as? String }
            ?: emptyList()

    override fun isWaiting(uuid: String): Boolean =
        redisTemplate.opsForZSet().score(ZSET_KEY, uuid) != null
}