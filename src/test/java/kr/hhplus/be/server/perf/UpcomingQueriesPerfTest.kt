package kr.hhplus.be.server.perf

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection

/**
 * 선택 심화 ① — "앞으로 생길 조회 기능" 시뮬레이션.
 *
 * 현재 API에는 없지만 과제 도메인(ERD `docs/project/erd.md`)상 곧 필요해질 조회 두 가지를
 * [IndexPerfTest]가 이미 적재한 공유 데이터([PerfDataSeeder], point_history=300,000 / reservation=200,000)로
 * 미리 시뮬레이션한다. 기존 인덱스가 충분한지 EXPLAIN + EXPLAIN ANALYZE로 실측한다.
 *
 * - 포인트 내역 조회: `point_history` by member, 최신순 페이지 — 이미 `idx_member_id_created_at`이 있다.
 * - 회원별 예약 목록 조회: `reservation` by member, 최신순 페이지 — 이미 `idx_member_id`가 있다(정렬 컬럼은 미포함).
 *
 * 원본 출력은 `docs/report/data/13-*.txt`에 남긴다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpcomingQueriesPerfTest : PerfTestBase() {

    private lateinit var conn: Connection
    private val repeats = 5

    @BeforeAll
    fun setup() {
        conn = PerfJdbc.rawConnection()
        PerfDataSeeder.ensureSeeded(conn)
        ReportWriter.ensureFreshSummary()
    }

    @AfterAll
    fun teardown() {
        conn.close()
    }

    // ────────────────────────────────────────────────────────────────
    // 포인트 내역 조회(예정) — point_history.idx_member_id_created_at
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("13. [예정] 포인트 내역 조회 — point_history(member_id, created_at) 최신순 페이지")
    fun pointHistoryByMemberLatestFirst() {
        val sql = "SELECT * FROM point_history WHERE member_id = 777 ORDER BY created_at DESC LIMIT 20"
        val scenario = "[예정]포인트내역조회(point_history.idx_member_id_created_at)"

        ExplainSupport.dropIndexIfExists(conn, "point_history", "idx_member_id_created_at")
        try {
            PerfMeasure.selectMedian(conn, repeats, scenario, "before(인덱스없음)", "before", sql, "13-point-history-by-member")
        } finally {
            ExplainSupport.execDdl(conn, "ALTER TABLE point_history ADD INDEX idx_member_id_created_at (member_id, created_at)")
        }
        PerfMeasure.selectMedian(conn, repeats, scenario, "after(인덱스있음=현재스키마)", "after", sql, "13-point-history-by-member")
    }

    // ────────────────────────────────────────────────────────────────
    // 회원별 예약 목록 조회(예정) — reservation.idx_member_id
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("14. [예정] 회원별 예약 목록 조회 — reservation(member_id) 최신순 페이지")
    fun reservationListByMemberLatestFirst() {
        val sql = "SELECT * FROM reservation WHERE member_id = 777 ORDER BY id DESC LIMIT 20"
        val scenario = "[예정]회원별예약목록조회(reservation.idx_member_id)"

        ExplainSupport.dropIndexIfExists(conn, "reservation", "idx_member_id")
        try {
            PerfMeasure.selectMedian(conn, repeats, scenario, "before(인덱스없음)", "before", sql, "14-reservation-by-member")
        } finally {
            ExplainSupport.execDdl(conn, "ALTER TABLE reservation ADD INDEX idx_member_id (member_id)")
        }
        PerfMeasure.selectMedian(conn, repeats, scenario, "after(인덱스있음=현재스키마)", "after", sql, "14-reservation-by-member")
    }
}
