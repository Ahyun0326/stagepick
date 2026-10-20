package kr.hhplus.be.server.infrastructure.transaction

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.transaction.support.TransactionSynchronizationManager

class SpringAfterCommitExecutorTest : FunSpec({

    val executor = SpringAfterCommitExecutor()

    afterEach {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    test("트랜잭션이 없으면 작업을 즉시 실행한다") {
        var executed = false

        executor.execute { executed = true }

        executed shouldBe true
    }

    test("트랜잭션 안에서는 등록만 하고, 커밋 이후(afterCommit)에 실행한다") {
        TransactionSynchronizationManager.initSynchronization()
        var executed = false

        executor.execute { executed = true }

        executed shouldBe false
        val synchronizations = TransactionSynchronizationManager.getSynchronizations()
        synchronizations shouldHaveSize 1

        synchronizations.first().afterCommit()
        executed shouldBe true
    }

    test("커밋 이후 작업의 예외는 호출자에게 전파하지 않는다") {
        TransactionSynchronizationManager.initSynchronization()

        executor.execute { throw IllegalStateException("redis down") }

        shouldNotThrowAny {
            TransactionSynchronizationManager.getSynchronizations().first().afterCommit()
        }
    }
})
