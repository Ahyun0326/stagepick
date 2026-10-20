package kr.hhplus.be.server.infrastructure.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.hhplus.be.server.domains.common.transaction.AfterCommitExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * TransactionSynchronization.afterCommit 기반 구현
 * - 등록 시점의 "현재" 트랜잭션(분산락 안의 REQUIRES_NEW 포함) 커밋 이후에 실행된다
 * - afterCommit 에서 던진 예외는 호출자에게 전파되어, 이미 커밋된 작업이 실패 응답으로 보이게 된다
 *   → 커밋 이후 작업의 예외는 잡아서 로그만 남긴다 (남은 Redis 항목은 만료 처리가 안전하게 정리)
 */
@Component
class SpringAfterCommitExecutor : AfterCommitExecutor {

    private val logger = KotlinLogging.logger {}

    override fun execute(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                runCatching(action).onFailure { e ->
                    logger.error(e) { "커밋 이후 작업 실패 - DB 는 이미 커밋됨, 후속 정리에 맡김" }
                }
            }
        })
    }
}
