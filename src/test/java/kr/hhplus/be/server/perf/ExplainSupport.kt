package kr.hhplus.be.server.perf

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * EXPLAIN / EXPLAIN ANALYZE 실행 + 반복 측정 유틸리티.
 *
 * - `EXPLAIN`은 실제로 실행하지 않고 실행 계획(type/key/rows/filtered/Extra)만 보여준다 → 결정적이라
 *   한 번만 수집한다.
 * - `EXPLAIN ANALYZE`는 실제로 실행하고 각 단계의 `actual time=시작..종료`를 보여준다(MySQL 8.0.18+) →
 *   캐시/락 경합에 따라 매 실행마다 흔들리므로 N회 반복해 중앙값을 쓴다.
 * - UPDATE 3종(조건부 UPDATE)은 재실행하면 데이터 상태가 바뀌어 버리므로(AVAILABLE→HOLD 등)
 *   `EXPLAIN UPDATE ...`(실행하지 않음)로 계획만 보고, 실제 시간은 상태를 되돌려가며
 *   `System.nanoTime()`으로 별도 측정한다 — [timedUpdateMedianMs] 참고.
 */
object ExplainSupport {

    data class PlanRow(
        val table: String?,
        val type: String?,
        val key: String?,
        val rows: Long?,
        val filtered: String?,
        val extra: String?
    )

    /** EXPLAIN 결과를 테이블별 한 줄씩(JOIN이면 여러 줄) 반환한다. */
    fun explain(conn: Connection, sql: String): List<PlanRow> {
        conn.createStatement().use { st ->
            st.executeQuery("EXPLAIN $sql").use { rs -> return readPlanRows(rs) }
        }
    }

    fun explainUpdate(conn: Connection, sql: String): List<PlanRow> = explain(conn, sql)

    private fun readPlanRows(rs: ResultSet): List<PlanRow> {
        val rows = mutableListOf<PlanRow>()
        while (rs.next()) {
            rows += PlanRow(
                table = runCatching { rs.getString("table") }.getOrNull(),
                type = runCatching { rs.getString("type") }.getOrNull(),
                key = runCatching { rs.getString("key") }.getOrNull(),
                rows = runCatching { rs.getLong("rows") }.getOrNull(),
                filtered = runCatching { rs.getString("filtered") }.getOrNull(),
                extra = runCatching { rs.getString("Extra") }.getOrNull()
            )
        }
        return rows
    }

    fun planRowsToText(rows: List<PlanRow>): String =
        rows.joinToString("\n") {
            "table=${it.table} type=${it.type} key=${it.key} rows=${it.rows} filtered=${it.filtered} Extra=${it.extra}"
        }

    /** EXPLAIN ANALYZE 원문(트리 텍스트)을 그대로 모은다. */
    fun explainAnalyzeRaw(conn: Connection, sql: String): String {
        conn.createStatement().use { st ->
            st.executeQuery("EXPLAIN ANALYZE $sql").use { rs ->
                val sb = StringBuilder()
                while (rs.next()) {
                    sb.appendLine(rs.getString(1))
                }
                return sb.toString()
            }
        }
    }

    // MySQL은 극히 짧은 시간을 "42e-6" 같은 과학적 표기로 쓴다(실측 확인: const 단일행 조회).
    // "[0-9.]+" 만으로는 지수부(e-6)를 못 읽어 "42e-6"을 "42"로 잘못 파싱하는 버그가 있었다 —
    // 숫자 그룹을 "가수부(.포함)+선택적 지수부" 형태로 명확히 분리해 "..".의 두 번째 점과 헷갈리지 않게 한다.
    private val FLOAT = """[0-9]+(?:\.[0-9]+)?(?:[eE][-+]?[0-9]+)?"""
    private val ACTUAL_TIME_REGEX = Regex("""actual time=($FLOAT)\.\.($FLOAT)""")

    /** 트리 최상단 "-> ... (actual time=시작..종료 rows=N loops=M)"에서 종료 시각(ms)을 추출한다. */
    fun topLevelActualTimeMs(analyzeText: String): Double? {
        val m = ACTUAL_TIME_REGEX.find(analyzeText) ?: return null
        return m.groupValues[2].toDoubleOrNull()
    }

    data class AnalyzeResult(val medianMs: Double, val samples: List<Double>, val rawOutputs: List<String>)

    fun repeatedAnalyze(conn: Connection, sql: String, repeats: Int = 5): AnalyzeResult {
        val raws = mutableListOf<String>()
        val samples = mutableListOf<Double>()
        repeat(repeats) {
            val raw = explainAnalyzeRaw(conn, sql)
            raws += raw
            topLevelActualTimeMs(raw)?.let { samples += it }
        }
        return AnalyzeResult(median(samples), samples, raws)
    }

    /** INSERT/UPDATE처럼 EXPLAIN ANALYZE 대상이 아니거나, 반복 실행하면 데이터 상태가 바뀌는 구문을
     *  wall-clock(System.nanoTime)으로 측정한다. [beforeEach]는 매 반복 전 상태 복원용 콜백. */
    fun timedExecuteMedianMs(
        repeats: Int = 5,
        beforeEach: () -> Unit = {},
        execute: () -> Unit
    ): Pair<Double, List<Double>> {
        val samples = mutableListOf<Double>()
        repeat(repeats) {
            beforeEach()
            val t0 = System.nanoTime()
            execute()
            val t1 = System.nanoTime()
            samples += (t1 - t0) / 1_000_000.0
        }
        return median(samples) to samples
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    fun dropIndexIfExists(conn: Connection, table: String, indexName: String) {
        conn.createStatement().use { st ->
            st.execute("ALTER TABLE `$table` DROP INDEX `$indexName`")
        }
    }

    fun execDdl(conn: Connection, ddl: String) {
        conn.createStatement().use { st -> st.execute(ddl) }
    }

    /**
     * FK가 걸린 컬럼의 "유일한" 인덱스는 InnoDB가 DROP INDEX 자체를 거부한다
     * (`Cannot drop index '...': needed in a foreign key constraint`, 실측 확인됨 — schedules.idx_concert_id_concerted_at,
     * payment.idx_reservation_id 모두 이 에러로 막혔다). 그 인덱스가 "없으면 어떤지"를 실측하려면
     * FK 제약을 먼저 떼어내야 한다. 측정이 끝나면 [recreateFk]로 반드시 복원한다.
     */
    fun dropForeignKeyOnColumn(conn: Connection, table: String, column: String): String {
        val name: String
        conn.prepareStatement(
            "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? " +
                    "AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1"
        ).use { ps ->
            ps.setString(1, table)
            ps.setString(2, column)
            ps.executeQuery().use { rs ->
                require(rs.next()) { "FK 제약을 찾지 못함: $table.$column" }
                name = rs.getString(1)
            }
        }
        execDdl(conn, "ALTER TABLE `$table` DROP FOREIGN KEY `$name`")
        return name
    }

    fun recreateFk(conn: Connection, table: String, constraintName: String, column: String, refTable: String, refColumn: String, onDelete: String? = null) {
        val suffix = if (onDelete != null) " ON DELETE $onDelete" else ""
        execDdl(
            conn,
            "ALTER TABLE `$table` ADD CONSTRAINT `$constraintName` FOREIGN KEY (`$column`) REFERENCES `$refTable` (`$refColumn`)$suffix"
        )
    }

    /** [indexName]을 잠깐 DROP한 채로 [block]을 실행하고(before 측정), 끝나면 [recreateDdl]로 되돌린다. */
    fun <T> withIndexTemporarilyDropped(
        conn: Connection,
        table: String,
        indexName: String,
        recreateDdl: String,
        block: () -> T
    ): T {
        dropIndexIfExists(conn, table, indexName)
        try {
            return block()
        } finally {
            execDdl(conn, recreateDdl)
        }
    }
}
