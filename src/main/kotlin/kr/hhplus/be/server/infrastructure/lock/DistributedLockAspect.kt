package kr.hhplus.be.server.infrastructure.lock

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

@Component
class DistributedLockAspect(
    innerRedissonClient: RedissonClient,
    innerDistributedLockTransactionProcessor: DistributedLockTransactionProcessor
) {
    init {
        redissonClient = innerRedissonClient
        distributedLockTransactionProcessor = innerDistributedLockTransactionProcessor
    }

    companion object {
        const val REDISSON_ROCK_PREFIX = "Lock:"

        lateinit var redissonClient: RedissonClient
            private set

        lateinit var distributedLockTransactionProcessor: DistributedLockTransactionProcessor
            private set
    }
}