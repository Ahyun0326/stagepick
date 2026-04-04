package kr.hhplus.be.server.infrastructure.lock

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.hhplus.be.server.domains.common.lock.LockManager
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class DistributedLockManager(
    private val redissonClient: RedissonClient,
    private val transactionProcessor: DistributedLockTransactionProcessor
): LockManager {

    private val logger = KotlinLogging.logger("Distributed Lock Logger")

    companion object {
        private const val REDISSON_LOCK_PREFIX = "Lock:"
    }

    override fun <T> runWithLock(key: String, waitTime: Long, leaseTime: Long, block: () -> T): T {
        val rLock = redissonClient.getLock("$REDISSON_LOCK_PREFIX:$key")

        try {
            val available = rLock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)
            if (!available) {
                logger.warn { "Failed to acquire lock key: $key name: ${rLock.name}" }
                throw RuntimeException("Failed to acquire lock")
            }

            logger.info { "Lock acquired successfully: $key" }
            return transactionProcessor.proceed { block() }  // 비즈니스 로직 실행

        } finally {
            if (rLock.isLocked && rLock.isHeldByCurrentThread) {
                logger.info { "Lock released successfully: $key" }
                rLock.unlock()
            }
        }
    }

}