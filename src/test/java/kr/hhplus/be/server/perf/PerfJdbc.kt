package kr.hhplus.be.server.perf

import kr.hhplus.be.server.TestcontainersConfiguration
import java.sql.Connection
import java.sql.DriverManager

/**
 * 성능 측정용 대량 시딩은 Hibernate/Hikari 풀을 거치지 않고 별도의 JDBC 커넥션으로 수행한다.
 *
 * - `rewriteBatchedStatements=true`: MySQL Connector/J가 batch INSERT를 다중 VALUES 1개 쿼리로
 *   재작성해 네트워크 라운드트립을 줄인다 (JDBC batch만으로는 효과가 거의 없음 — Connector/J 기본값이 false).
 * - Hikari 커넥션 풀(maximum-pool-size=3)은 애플리케이션 트래픽 재현용이라 대량 시딩에는 적합하지 않아
 *   시딩 전용 단일 커넥션을 직접 연다.
 */
object PerfJdbc {

    fun rawConnection(): Connection {
        val base = TestcontainersConfiguration.mySqlContainer.jdbcUrl
        val url = "$base?characterEncoding=UTF-8&serverTimezone=UTC" +
                "&rewriteBatchedStatements=true&useServerPrepStmts=true&cachePrepStmts=true"
        return DriverManager.getConnection(
            url,
            TestcontainersConfiguration.mySqlContainer.username,
            TestcontainersConfiguration.mySqlContainer.password
        )
    }
}
