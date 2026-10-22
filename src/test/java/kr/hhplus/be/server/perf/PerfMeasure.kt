package kr.hhplus.be.server.perf

import java.sql.Connection

/**
 * `SELECT` 쿼리를 EXPLAIN + EXPLAIN ANALYZE(반복 → 중앙값)로 측정하고,
 * 원본 출력을 `docs/report/data/{fileLabel}-{fileTag}.txt`에, 요약 한 줄을 `summary.csv`에 남기는 공통 로직.
 *
 * [IndexPerfTest], [TableRedesignPerfTest], [UpcomingQueriesPerfTest]가 공유한다 — 여러 perf 테스트
 * 클래스에서 같은 측정 패턴(EXPLAIN 수집 → N회 반복 → 중앙값 → 원본/요약 기록)이 반복되므로
 * 한 곳으로 모아 중복을 없앴다(파일 하나당 역할 하나 — coding-style 가이드의 "유틸리티 추출" 원칙).
 *
 * @param fileTag 파일명에 쓰는 ASCII 태그(예: "before"/"after"). `phase`(한글 설명, summary.csv용)와
 *   분리한 이유는 §요구사항 — `docs/report/data/` 파일명을 한글/괄호/쉼표 없는 ASCII 케밥 케이스로 정리하기 위함.
 */
object PerfMeasure {

    fun selectMedian(
        conn: Connection,
        repeats: Int,
        scenario: String,
        phase: String,
        fileTag: String,
        sql: String,
        fileLabel: String
    ): ExplainSupport.AnalyzeResult {
        val plan = ExplainSupport.explain(conn, sql)
        val analyze = ExplainSupport.repeatedAnalyze(conn, sql, repeats)

        val raw = buildString {
            appendLine("SQL: $sql")
            appendLine()
            appendLine("--- EXPLAIN ---")
            appendLine(ExplainSupport.planRowsToText(plan))
            appendLine()
            appendLine("--- EXPLAIN ANALYZE 반복 ${repeats}회 (ms, 전체 쿼리 최상위 노드 actual time 종료값) ---")
            analyze.samples.forEachIndexed { i, v -> appendLine("rep${i + 1} = ${"%.6f".format(v)}ms") }
            appendLine("median = ${"%.6f".format(analyze.medianMs)}ms")
            appendLine()
            appendLine("--- EXPLAIN ANALYZE 원문 (1회차) ---")
            appendLine(analyze.rawOutputs.firstOrNull() ?: "")
        }
        ReportWriter.writeRaw("$fileLabel-$fileTag", raw)

        val main = plan.firstOrNull()
        ReportWriter.appendSummary(
            scenario, phase, main?.table, main?.type, main?.key, main?.rows, main?.filtered, main?.extra,
            analyze.medianMs, repeats
        )
        plan.drop(1).forEach { row ->
            ReportWriter.appendSummary(
                scenario, phase, row.table, row.type, row.key, row.rows, row.filtered, row.extra,
                null, repeats, note = "join 대상 테이블(전체 쿼리 시간은 첫 행 참고)"
            )
        }
        return analyze
    }
}
