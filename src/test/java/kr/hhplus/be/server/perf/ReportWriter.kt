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

    // perfTest 태스크 한 번 실행(=JVM 프로세스 한 번) 안에서 여러 perf 테스트 클래스
    // (IndexPerfTest, TableRedesignPerfTest, UpcomingQueriesPerfTest)가 같은 summary.csv에 이어 쓴다.
    // 클래스 실행 순서는 JUnit이 보장하지 않으므로, "가장 먼저 호출된 쪽"만 헤더를 새로 쓰고
    // 그 뒤로는 모두 append만 하도록 멱등성을 둔다 — 그렇지 않으면 나중에 실행된 클래스의
    // resetSummary가 앞서 쓴 다른 클래스의 결과를 지워버린다.
    @Volatile
    private var freshForThisRun = false

    @Synchronized
    fun ensureFreshSummary() {
        if (freshForThisRun) return
        summaryFile.writeText("scenario,phase,table,type,key,rows,filtered,extra,median_ms,reps,note\n")
        freshForThisRun = true
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
