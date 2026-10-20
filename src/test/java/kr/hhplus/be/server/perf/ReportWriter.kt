package kr.hhplus.be.server.perf

import java.io.File

/**
 * perfTest 측정 원본을 `docs/report/data/`에 남긴다.
 * - `*.txt`: 시나리오별 EXPLAIN / EXPLAIN ANALYZE 원문 전체
 * - `summary.csv`: 보고서 표에 그대로 옮길 수 있는 요약 한 줄씩 (전/후 비교용)
 *
 * `docs/report/step4-index-report.md`는 이 디렉토리의 값만 인용한다 — 측정하지 않은 수치를
 * 보고서에 적지 않기 위한 장치.
 */
object ReportWriter {
    // Gradle 테스트의 작업 디렉터리 = 프로젝트 루트
    private val dir = File(System.getProperty("user.dir"), "docs/report/data").apply { mkdirs() }
    private val summaryFile = File(dir, "summary.csv")

    @Synchronized
    fun resetSummary() {
        summaryFile.writeText("scenario,phase,table,type,key,rows,filtered,extra,median_ms,reps,note\n")
    }

    @Synchronized
    fun appendSummary(
        scenario: String,
        phase: String,
        table: String?,
        type: String?,
        key: String?,
        rows: Long?,
        filtered: String?,
        extra: String?,
        medianMs: Double?,
        reps: Int,
        note: String = ""
    ) {
        val line = listOf(scenario, phase, table, type, key, rows, filtered, extra, medianMs, reps, note)
            .joinToString(",") { v -> "\"${(v?.toString() ?: "").replace("\"", "'")}\"" }
        summaryFile.appendText("$line\n")
    }

    fun writeRaw(scenarioFileName: String, content: String) {
        File(dir, "$scenarioFileName.txt").writeText(content)
    }
}
