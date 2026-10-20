package kr.hhplus.be.server.domains.common.transaction

/**
 * 현재 트랜잭션이 커밋된 "뒤"에 작업을 실행한다
 * - DB 트랜잭션이 되돌려 주지 못하는 외부 저장소(Redis 등) 정리를 커밋 이후로 미룰 때 사용
 * - 트랜잭션이 롤백되면 작업은 실행되지 않는다
 * - 트랜잭션이 없으면 즉시 실행한다
 */
interface AfterCommitExecutor {
    fun execute(action: () -> Unit)
}
