package kr.hhplus.be.server.perf

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 선택 심화 ① — "테이블 재설계" 솔루션 실측.
 *
 * `docs/report/step4-index-report.md` §4의 콘서트 목록 집계 쿼리(`ConcertRepositoryImpl.findConcerts`)는
 * 인덱스로는 개선되지 않는다는 게 [IndexPerfTest]에서 이미 실측됐다(6.2ms vs 5.75ms, 유의미한 차이 없음).
 * 이 클래스는 "인덱스가 아니라 테이블 구조를 바꾸면 어떨까"를 프로토타입 테이블로 직접 만들어 실측한다.
 *
 * 운영 중인 `concert`/`schedules` 테이블은 다른 perf 테스트([IndexPerfTest])와 통합 테스트가 공유하는
 * 고정 규모(concert=1,000 / schedules=10,000) 데이터라 건드리지 않고, 이 클래스 전용 프로토타입
 * 테이블(`bench_*`)을 새로 만들어 쓰고 끝나면 지운다.
 *
 * - 실험 A: 현재 구조(LEFT JOIN + GROUP BY MIN/MAX) vs 재설계안(콘서트에 최초/최종 공연일 비정규화 컬럼을
 *   추가해 단일 테이블 조회)을 회차(schedules) 건수 10,000 / 100,000 / 300,000 규모에서 비교해
 *   "데이터가 늘어도 재설계안은 시간이 느는가"를 확인한다.
 * - 실험 B: 재설계안의 쓰기 경로 비용 — 회차 생성 시 콘서트의 비정규화 컬럼을 같이 갱신하는 비용.
 * - 실험 C: 페이지네이션 — 목록에 LIMIT이 없을 때 데이터가 늘면 응답이 선형으로 늘어나는 것과,
 *   같은 데이터에서 오프셋 페이지네이션이 깊은 페이지일수록 느려지는 것 vs 커서 페이지네이션은 그렇지 않음을 비교한다.
 *
 * 원본 출력은 `docs/report/data/10-*.txt`, `11-*.txt`에 남긴다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TableRedesignPerfTest : PerfTestBase() {

    private lateinit var conn: Connection
    private val repeats = 5

    companion object {
        const val CONCERT_COUNT = 1_000
        val SCHEDULE_SCALES = listOf(10_000, 100_000, 300_000)
        const val PAGING_LARGE_COUNT = 100_000
    }

    @BeforeAll
    fun setup() {
        conn = PerfJdbc.rawConnection()
        ReportWriter.ensureFreshSummary()
        createBenchTables()
        seedConcertRows()
    }

    @AfterAll
    fun teardown() {
        dropBenchTables()
        conn.close()
    }

    private fun createBenchTables() {
        conn.createStatement().use { st ->
            st.execute("DROP TABLE IF EXISTS bench_schedule_current")
            st.execute("DROP TABLE IF EXISTS bench_concert_current")
            st.execute("DROP TABLE IF EXISTS bench_concert_redesign")
            st.execute("DROP TABLE IF EXISTS bench_concert_paging_large")

            st.execute(
                """
                CREATE TABLE bench_concert_current (
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(100) NOT NULL,
                    location VARCHAR(100) NOT NULL
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE bench_schedule_current (
                    schedule_id BIGINT PRIMARY KEY,
                    concert_id BIGINT NOT NULL,
                    concerted_at DATETIME NOT NULL,
                    viewing_time INT NOT NULL,
                    INDEX idx_concert_id_concerted_at (concert_id, concerted_at)
                )
                """.trimIndent()
            )
            // 재설계안: concert에 최초/최종 공연일을 비정규화 컬럼으로 추가 — join 없이 목록을 조회한다.
            st.execute(
                """
                CREATE TABLE bench_concert_redesign (
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(100) NOT NULL,
                    location VARCHAR(100) NOT NULL,
                    first_concerted_at DATETIME NULL,
                    last_concerted_at DATETIME NULL
                )
                """.trimIndent()
            )
            // 페이지네이션(실험 C) 비교용 대량 테이블 — bench_concert_redesign과 같은 스키마지만
            // CONCERT_COUNT(1,000)보다 훨씬 큰 PAGING_LARGE_COUNT(100,000)행으로 "응답 크기 증가"를 보여준다.
            st.execute(
                """
                CREATE TABLE bench_concert_paging_large (
                    id BIGINT PRIMARY KEY,
                    title VARCHAR(100) NOT NULL,
                    location VARCHAR(100) NOT NULL,
                    first_concerted_at DATETIME NULL,
                    last_concerted_at DATETIME NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun dropBenchTables() {
        conn.createStatement().use { st ->
            listOf(
                "bench_schedule_current", "bench_concert_current",
                "bench_concert_redesign", "bench_concert_paging_large"
            ).forEach { st.execute("DROP TABLE IF EXISTS `$it`") }
        }
    }

    private fun now() = Timestamp.valueOf(LocalDateTime.now())

    /** concert 행(1,000건)과 큰 페이징용 concert 행(100,000건)은 전체 실험에서 공통이라 한 번만 심는다. */
    private fun seedConcertRows() {
        conn.autoCommit = false
        conn.prepareStatement("INSERT INTO bench_concert_current (id, title, location) VALUES (?,?,?)").use { ps ->
            for (id in 1..CONCERT_COUNT) {
                ps.setLong(1, id.toLong())
                ps.setString(2, "콘서트 #$id")
                ps.setString(3, "장소 #${id % 50}")
                ps.addBatch()
            }
            ps.executeBatch()
        }
        conn.prepareStatement(
            "INSERT INTO bench_concert_redesign (id, title, location, first_concerted_at, last_concerted_at) VALUES (?,?,?,NULL,NULL)"
        ).use { ps ->
            for (id in 1..CONCERT_COUNT) {
                ps.setLong(1, id.toLong())
                ps.setString(2, "콘서트 #$id")
                ps.setString(3, "장소 #${id % 50}")
                ps.addBatch()
            }
            ps.executeBatch()
        }
        conn.prepareStatement(
            "INSERT INTO bench_concert_paging_large (id, title, location, first_concerted_at, last_concerted_at) VALUES (?,?,?,?,?)"
        ).use { ps ->
            for (id in 1..PAGING_LARGE_COUNT) {
                ps.setLong(1, id.toLong())
                ps.setString(2, "콘서트 #$id")
                ps.setString(3, "장소 #${id % 50}")
                ps.setTimestamp(4, now())
                ps.setTimestamp(5, now())
                ps.addBatch()
                if (id % 5_000 == 0) ps.executeBatch()
            }
            ps.executeBatch()
        }
        conn.commit()
        conn.autoCommit = true
    }

    private fun reseedSchedules(scale: Int) {
        conn.createStatement().use { it.execute("TRUNCATE TABLE bench_schedule_current") }
        conn.autoCommit = false
        conn.prepareStatement(
            "INSERT INTO bench_schedule_current (schedule_id, concert_id, concerted_at, viewing_time) VALUES (?,?,?,?)"
        ).use { ps ->
            for (id in 1..scale) {
                val concertId = ((id - 1) % CONCERT_COUNT) + 1L
                ps.setLong(1, id.toLong())
                ps.setLong(2, concertId)
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().plusMinutes(id.toLong())))
                ps.setInt(4, 120)
                ps.addBatch()
                if (id % 5_000 == 0) {
                    ps.executeBatch()
                    conn.commit()
                }
            }
            ps.executeBatch()
            conn.commit()
        }
        conn.autoCommit = true

        // 재설계 테이블의 비정규화 컬럼을 이번 스케일의 schedules로부터 다시 계산한다.
        // (실제 운영에서는 회차 생성/변경 시 건건이 갱신 — 그 비용은 실험 B에서 별도로 측정한다.)
        conn.createStatement().use { st ->
            st.execute(
                """
                UPDATE bench_concert_redesign r
                JOIN (
                    SELECT concert_id, MIN(concerted_at) AS mn, MAX(concerted_at) AS mx
                    FROM bench_schedule_current GROUP BY concert_id
                ) s ON r.id = s.concert_id
                SET r.first_concerted_at = s.mn, r.last_concerted_at = s.mx
                """.trimIndent()
            )
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 실험 A: 현재 구조(join+group) vs 재설계안(비정규화 단일 테이블) — 회차 건수 증가 추세
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("10. 테이블 재설계 — 콘서트 목록 집계: join+group vs 비정규화 단일 테이블 (schedules 10k/100k/300k)")
    fun concertListRedesignAcrossScale() {
        val currentSql = "SELECT c.id, c.title, c.location, MIN(s.concerted_at), MAX(s.concerted_at) " +
                "FROM bench_concert_current c LEFT JOIN bench_schedule_current s ON c.id = s.concert_id " +
                "GROUP BY c.id, c.title, c.location"
        val redesignSql = "SELECT id, title, location, first_concerted_at, last_concerted_at FROM bench_concert_redesign"

        SCHEDULE_SCALES.forEach { scale ->
            reseedSchedules(scale)
            val scaleTag = scaleLabel(scale)
            PerfMeasure.selectMedian(
                conn, repeats,
                scenario = "콘서트목록_현재구조(join+group, schedules=$scale)",
                phase = "현재구조($scaleTag)",
                fileTag = "current-$scaleTag",
                sql = currentSql,
                fileLabel = "10-redesign-concert-list"
            )
            PerfMeasure.selectMedian(
                conn, repeats,
                scenario = "콘서트목록_재설계안(비정규화 단일테이블, schedules=$scale)",
                phase = "재설계안($scaleTag)",
                fileTag = "redesigned-$scaleTag",
                sql = redesignSql,
                fileLabel = "10-redesign-concert-list"
            )
        }
    }

    private fun scaleLabel(scale: Int) = when (scale) {
        1_000 -> "1k"
        10_000 -> "10k"
        100_000 -> "100k"
        300_000 -> "300k"
        else -> "${scale}"
    }

    // ────────────────────────────────────────────────────────────────
    // 실험 B: 재설계안의 쓰기 경로 비용 — 회차 생성 시 concert 비정규화 컬럼 동시 갱신
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("11. 테이블 재설계 — 쓰기 경로 비용: 회차 생성만 vs 회차 생성 + concert 집계컬럼 갱신")
    fun writePathCostOfDenormalizedColumns() {
        // 10) 테스트가 바꿔놓은 schedules 규모와 무관하게 동작하도록, 쓰기 비용 측정 전용 ID 대역(5천만 이상)을 쓴다.
        val n = 2_000
        val baseId = 50_000_000L
        val insertScheduleSql =
            "INSERT INTO bench_schedule_current (schedule_id, concert_id, concerted_at, viewing_time) VALUES (?,?,?,?)"
        val updateAggSql = "UPDATE bench_concert_redesign SET " +
                "first_concerted_at = LEAST(COALESCE(first_concerted_at, ?), ?), " +
                "last_concerted_at = GREATEST(COALESCE(last_concerted_at, ?), ?) " +
                "WHERE id = ?"

        fun cleanup() {
            conn.createStatement().use { it.execute("DELETE FROM bench_schedule_current WHERE schedule_id >= $baseId") }
        }

        // 시나리오 1: 회차 INSERT만 (현재 구조와 동일한 쓰기 비용)
        val (medianInsertOnly, samplesInsertOnly) = ExplainSupport.timedExecuteMedianMs(repeats = 3, beforeEach = ::cleanup) {
            conn.prepareStatement(insertScheduleSql).use { ps ->
                for (i in 0 until n) {
                    val id = baseId + i
                    val concertId = (i % CONCERT_COUNT) + 1L
                    ps.setLong(1, id)
                    ps.setLong(2, concertId)
                    ps.setTimestamp(3, now())
                    ps.setInt(4, 120)
                    ps.executeUpdate()
                }
            }
        }

        // 시나리오 2: 회차 INSERT + concert 비정규화 컬럼 UPDATE (재설계안의 쓰기 경로)
        val (medianWithUpdate, samplesWithUpdate) = ExplainSupport.timedExecuteMedianMs(repeats = 3, beforeEach = ::cleanup) {
            conn.prepareStatement(insertScheduleSql).use { insertPs ->
                conn.prepareStatement(updateAggSql).use { updatePs ->
                    for (i in 0 until n) {
                        val id = baseId + i
                        val concertId = (i % CONCERT_COUNT) + 1L
                        val ts = now()
                        insertPs.setLong(1, id)
                        insertPs.setLong(2, concertId)
                        insertPs.setTimestamp(3, ts)
                        insertPs.setInt(4, 120)
                        insertPs.executeUpdate()

                        updatePs.setTimestamp(1, ts)
                        updatePs.setTimestamp(2, ts)
                        updatePs.setTimestamp(3, ts)
                        updatePs.setTimestamp(4, ts)
                        updatePs.setLong(5, concertId)
                        updatePs.executeUpdate()
                    }
                }
            }
        }
        cleanup()

        ReportWriter.writeRaw(
            "11-redesign-write-cost-insert-only",
            "rowsToInsert=$n reps=3 (회차 INSERT만, 단건 커밋 없음=autocommit 기본)\n" +
                    samplesInsertOnly.mapIndexed { i, v -> "rep${i + 1}=${"%.1f".format(v)}ms" }.joinToString("\n") +
                    "\nmedian=${"%.1f".format(medianInsertOnly)}ms\nper-row=${"%.4f".format(medianInsertOnly / n)}ms"
        )
        ReportWriter.writeRaw(
            "11-redesign-write-cost-insert-plus-update",
            "rowsToInsert=$n reps=3 (회차 INSERT + concert.first/last_concerted_at UPDATE)\n" +
                    samplesWithUpdate.mapIndexed { i, v -> "rep${i + 1}=${"%.1f".format(v)}ms" }.joinToString("\n") +
                    "\nmedian=${"%.1f".format(medianWithUpdate)}ms\nper-row=${"%.4f".format(medianWithUpdate / n)}ms"
        )
        ReportWriter.appendSummary(
            "재설계_쓰기비용_회차생성만", "현재구조와동일", "bench_schedule_current", "INSERT", null, n.toLong(), null, null,
            medianInsertOnly, 3, "단건 INSERT ${n}회 순차 실행(실시간 API 호출 1건=1행 삽입을 흉내)"
        )
        ReportWriter.appendSummary(
            "재설계_쓰기비용_회차생성+집계컬럼갱신", "재설계안의 쓰기경로", "bench_concert_redesign", "UPDATE", null, n.toLong(), null, null,
            medianWithUpdate, 3, "단건 INSERT+UPDATE ${n}회 순차 실행"
        )
    }

    // ────────────────────────────────────────────────────────────────
    // 실험 C: 페이지네이션 — 무제한 목록의 크기 증가 + 오프셋 vs 커서
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("12. 페이지네이션 — LIMIT 없는 전체 조회 크기 증가 + 오프셋 vs 커서(얕음/깊음)")
    fun paginationOffsetVsCursor() {
        val fullScanSmall = "SELECT id, title, location, first_concerted_at, last_concerted_at FROM bench_concert_redesign"
        val fullScanLarge = "SELECT id, title, location, first_concerted_at, last_concerted_at FROM bench_concert_paging_large"
        val offsetShallow = "SELECT id, title, location, first_concerted_at, last_concerted_at " +
                "FROM bench_concert_paging_large ORDER BY id LIMIT 20 OFFSET 20"
        val offsetDeep = "SELECT id, title, location, first_concerted_at, last_concerted_at " +
                "FROM bench_concert_paging_large ORDER BY id LIMIT 20 OFFSET 99960"
        val cursorShallow = "SELECT id, title, location, first_concerted_at, last_concerted_at " +
                "FROM bench_concert_paging_large WHERE id > 20 ORDER BY id LIMIT 20"
        val cursorDeep = "SELECT id, title, location, first_concerted_at, last_concerted_at " +
                "FROM bench_concert_paging_large WHERE id > 99960 ORDER BY id LIMIT 20"

        PerfMeasure.selectMedian(conn, repeats, "목록크기증가_LIMIT없음(concert=1000)", "작음(1,000행)", "full-scan-small", fullScanSmall, "12-redesign-pagination")
        PerfMeasure.selectMedian(conn, repeats, "목록크기증가_LIMIT없음(concert=100000)", "큼(100,000행)", "full-scan-large", fullScanLarge, "12-redesign-pagination")
        PerfMeasure.selectMedian(conn, repeats, "오프셋페이지네이션(concert=100000)", "얕음(OFFSET 20)", "offset-shallow", offsetShallow, "12-redesign-pagination")
        PerfMeasure.selectMedian(conn, repeats, "오프셋페이지네이션(concert=100000)", "깊음(OFFSET 99960)", "offset-deep", offsetDeep, "12-redesign-pagination")
        PerfMeasure.selectMedian(conn, repeats, "커서페이지네이션(concert=100000)", "얕음(id>20)", "cursor-shallow", cursorShallow, "12-redesign-pagination")
        PerfMeasure.selectMedian(conn, repeats, "커서페이지네이션(concert=100000)", "깊음(id>99960)", "cursor-deep", cursorDeep, "12-redesign-pagination")
    }
}
