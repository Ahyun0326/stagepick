package kr.hhplus.be.server.domains.common.lock

/**
 * 데이터 정합성 보장을 위해 대상 함수는 별도의 트랜잭션으로 동작하며 트랜잭션 커밋 이후 락 해제
 */

interface LockManager {
    fun <T> runWithLock(
        key: String,
        waitTime: Long = 5L,
        leaseTime: Long = 3L,
        block: () -> T
    ): T
}