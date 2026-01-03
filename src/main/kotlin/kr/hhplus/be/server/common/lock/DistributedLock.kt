package kr.hhplus.be.server.common.lock

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit

/**
 * 데이터 정합성 보장을 위해 대상 함수는 별도의 트랜잭션으로 동작하며 트랜잭션 커밋 이후 락 해제
 */

private val logger = KotlinLogging.logger("Distributed Lock Logger")

fun <T> distributedLock(
    key: String,
    waitTime: Long = 5,
    leaseTime: Long = 3,
    function: () -> T
): T {
    val rLock = DistributedLockAspect.redissonClient
        .getLock(DistributedLockAspect.REDISSON_ROCK_PREFIX + key)

    try {
        val available = rLock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)
        if (!available) {
            logger.warn { "Failed to acquire lock key: $key name: ${rLock.name}" }
            throw RuntimeException("Failed to acquire lock")
        }

        logger.info { "Lock acquired successfully: $key" }
        return DistributedLockAspect.distributedLockTransactionProcessor.proceed { function() }  // 비즈니스 로직 실행

    } finally {
        if (rLock.isLocked && rLock.isHeldByCurrentThread) {
            logger.info { "Lock released successfully: $key" }
            rLock.unlock()
        }
    }
}
