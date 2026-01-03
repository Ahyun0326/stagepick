package kr.hhplus.be.server.common.lock

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 트랜잭션 분리를 위한 클래스
 * 부모 트랜잭션 유무에 관계없이 별도의 트랜잭션으로 동작
 */
@Component
class DistributedLockTransactionProcessor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun <T> proceed(function: () -> T): T {
        return function()
    }
}