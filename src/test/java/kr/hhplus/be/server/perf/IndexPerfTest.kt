package kr.hhplus.be.server.perf

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection

/**
 * 선택 심화 ① — 느린 조회 리스트업 → EXPLAIN/EXPLAIN ANALYZE 실측 → 인덱스 전/후 비교.
 *
 * 실행: `./gradlew perfTest` (기본 `./gradlew test`에는 포함되지 않음 — [PerfTestBase] 참고)
 *
 * 각 시나리오는:
 *  1. 후보 인덱스를 DROP한 "before" 상태에서 EXPLAIN + EXPLAIN ANALYZE(5회 반복, 중앙값)를 수집하고
 *  2. 인덱스를 되돌린 "after" 상태에서 동일하게 수집한 뒤
 *  3. 반드시 원래 인덱스 상태로 복원한다 (다른 시나리오·다른 perfTest 실행에 영향 없게).
 *
 * UPDATE 3종(조건부 UPDATE)은 MySQL 8.0.46에서 `EXPLAIN ANALYZE UPDATE`가
 * "not executable by iterator executor"로 거부되어(실측 확인됨) EXPLAIN(계획)만 수집하고,
 * 실행 시간은 상태를 매 반복 복원하며 `System.nanoTime()`으로 직접 측정한다.
 *
 * 원본 출력은 `docs/report/data` 아래 txt 파일들, 요약은 `docs/report/data/summary.csv`에 남긴다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexPerfTest : PerfTestBase() {

    private lateinit var conn: Connection

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

    private val repeats = 5

    /**
     * @param fileTag 원본 txt 파일명에 쓰는 ASCII 태그("before"/"after" 등). `phase`(한글 설명)는
     *   summary.csv에만 쓰고, 파일명에는 쓰지 않는다 — `docs/report/data/` 파일명을 한글·괄호·쉼표 없는
     *   ASCII 케밥 케이스로 유지하기 위함.
     */
    private fun measureSelect(scenario: String, phase: String, fileTag: String, sql: String, fileLabel: String) {
        PerfMeasure.selectMedian(conn, repeats, scenario, phase, fileTag, sql, fileLabel)
    }

    // ────────────────────────────────────────────────────────────────
    // 1) 콘서트 목록 집계 — schedules.idx_concert_id_concerted_at
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("1. 콘서트 목록 MIN/MAX 집계 — schedules(concert_id, concerted_at)")
    fun concertListAggregate() {
        val sql = "SELECT c.id, c.title, c.location, MIN(s.concerted_at), MAX(s.concerted_at) " +
                "FROM concert c LEFT JOIN schedules s ON c.id = s.concert_id " +
                "GROUP BY c.id, c.title, c.location"
        // ScheduleRepositoryImpl.findAvailableConcerts와 동일한 형태 — 같은 인덱스를 선택적 WHERE로 쓰는 쿼리라
        // 집계 쿼리(sql)와 대조해 "이 인덱스가 어떤 쿼리에 실제로 득이 되는지"를 함께 보여준다.
        val sqlByConcertId = "SELECT * FROM schedules WHERE concert_id = 500"
        val scenario = "콘서트목록집계(schedules.idx_concert_id_concerted_at)"
        val scenarioByConcertId = "회차목록조회_concertId단건(schedules.idx_concert_id_concerted_at)"

        // idx_concert_id_concerted_at 는 schedules.concert_id FK가 의존하는 유일한 인덱스라
        // DROP INDEX 자체가 거부된다(실측 확인: "Cannot drop index ...: needed in a foreign key constraint").
        // FK를 먼저 떼어내야 "인덱스 없음" 상태를 만들 수 있다.
        val fkName = ExplainSupport.dropForeignKeyOnColumn(conn, "schedules", "concert_id")
        ExplainSupport.dropIndexIfExists(conn, "schedules", "idx_concert_id_concerted_at")
        try {
            measureSelect(scenario, "before(인덱스없음)", "before", sql, "01-concert-list-agg")
            measureSelect(scenarioByConcertId, "before(인덱스없음)", "before", sqlByConcertId, "01b-schedule-by-concert-id")
        } finally {
            ExplainSupport.execDdl(conn, "ALTER TABLE schedules ADD INDEX idx_concert_id_concerted_at (concert_id, concerted_at)")
            ExplainSupport.recreateFk(conn, "schedules", fkName, "concert_id", "concert", "id", onDelete = "CASCADE")
        }
        measureSelect(scenario, "after(인덱스있음=현재코드)", "after", sql, "01-concert-list-agg")
        measureSelect(scenarioByConcertId, "after(인덱스있음=현재코드)", "after", sqlByConcertId, "01b-schedule-by-concert-id")
    }

    // ────────────────────────────────────────────────────────────────
    // 2) 좌석 역조회 + 3) 예약 상세 조회 — seat.idx_reservation_id 공유 측정
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("2-3. 좌석 역조회/예약 상세 조회 — seat(reservation_id)")
    fun seatReservationIdLookups() {
        val seatByReservation = "SELECT * FROM seat WHERE reservation_id = 777"
        val reservationDetail = "SELECT seat.reservation_id, concert.title, concert.location, schedules.viewing_time, " +
                "schedules.concerted_at, seat.number, seat.price " +
                "FROM seat " +
                "INNER JOIN schedules ON seat.schedule_id = schedules.schedule_id " +
                "INNER JOIN concert ON schedules.concert_id = concert.id " +
                "WHERE seat.reservation_id = 777"

        ExplainSupport.dropIndexIfExists(conn, "seat", "idx_reservation_id")
        try {
            measureSelect("좌석역조회(seat.idx_reservation_id)", "before(인덱스없음)", "before", seatByReservation, "02-seat-by-reservation-id")
            measureSelect("예약상세조회(seat.idx_reservation_id)", "before(인덱스없음)", "before", reservationDetail, "03-reservation-detail")
        } finally {
            ExplainSupport.execDdl(conn, "ALTER TABLE seat ADD INDEX idx_reservation_id (reservation_id)")
        }
        measureSelect("좌석역조회(seat.idx_reservation_id)", "after(인덱스있음=현재코드)", "after", seatByReservation, "02-seat-by-reservation-id")
        measureSelect("예약상세조회(seat.idx_reservation_id)", "after(인덱스있음=현재코드)", "after", reservationDetail, "03-reservation-detail")
    }

    // ────────────────────────────────────────────────────────────────
    // 4) 결제 조회 — payment.idx_reservation_id
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("4. 결제 조회 findByReservationId — payment(reservation_id)")
    fun paymentByReservationId() {
        val sql = "SELECT * FROM payment WHERE reservation_id = 777"
        val scenario = "결제조회(payment.uk_payment_reservation_id)"

        // Step4 선택 심화 ②(동시성, docs/report/step4-concurrency-report.md) 이후 이 컬럼의 인덱스는
        // 더 이상 평범한 INDEX(idx_reservation_id)가 아니라 UNIQUE 제약(uk_payment_reservation_id)이다
        // — 예약당 결제 1건을 DB 수준에서 강제한다(PaymentEntity.kt 참고). 그 제약이 거는 유일한 인덱스라
        // payment.reservation_id FK가 여기 의존해 DROP INDEX 자체가 거부된다(schedules 케이스와 동일한 이유).
        val fkName = ExplainSupport.dropForeignKeyOnColumn(conn, "payment", "reservation_id")
        ExplainSupport.dropIndexIfExists(conn, "payment", "uk_payment_reservation_id")
        try {
            measureSelect(scenario, "before(인덱스없음)", "before", sql, "04-payment-by-reservation-id")
        } finally {
            // 원래대로 "UNIQUE 제약"으로 복원한다 — 단순 INDEX로 복원하면 예약당 결제 1건 보장이 깨진 채로
            // 이후 측정이 진행되므로, 반드시 현재 코드(PaymentEntity.kt)와 같은 형태로 되돌린다.
            ExplainSupport.execDdl(conn, "ALTER TABLE payment ADD CONSTRAINT uk_payment_reservation_id UNIQUE (reservation_id)")
            ExplainSupport.recreateFk(conn, "payment", fkName, "reservation_id", "reservation", "id")
        }
        measureSelect(scenario, "after(인덱스있음=현재코드)", "after", sql, "04-payment-by-reservation-id")
    }

    // ────────────────────────────────────────────────────────────────
    // 5) 결제 중복 인덱스 — payment.number UNIQUE 제약과 idx_number
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("5. 결제번호 조회/쓰기 비용 — payment.number UNIQUE vs idx_number 중복")
    fun paymentNumberDuplicateIndex() {
        val sql = "SELECT * FROM payment WHERE number = 'P00000000000777'"
        val scenario = "결제번호중복인덱스(payment.idx_number)"

        // PaymentEntity 에서 idx_number 를 이미 제거했으므로(이 보고서의 결정 사항), 스키마는
        // 기본적으로 "제거 후" 상태다. idx_number가 "있었을 때"를 재현하려면 거꾸로 임시로 추가해야 한다.
        measureSelect(scenario, "제거후(현재코드, unique만 있음)", "after", sql, "05-payment-number-read")
        measureWriteCost("결제번호중복인덱스_쓰기비용", "제거후(현재코드)", "after", withIdxNumber = false)

        ExplainSupport.execDdl(conn, "ALTER TABLE payment ADD INDEX idx_number (number)")
        try {
            measureSelect(scenario, "제거전(unique+idx_number둘다있음, 가정)", "before", sql, "05-payment-number-read")
            measureWriteCost("결제번호중복인덱스_쓰기비용", "제거전(unique+idx_number)", "before", withIdxNumber = true)
        } finally {
            ExplainSupport.dropIndexIfExists(conn, "payment", "idx_number")
        }
    }

    /** payment와 동일한 구조의 임시 테이블에 2만 건 batch INSERT 해 중복 인덱스의 쓰기 비용을 비교한다. */
    private fun measureWriteCost(scenario: String, phase: String, fileTag: String, withIdxNumber: Boolean) {
        conn.createStatement().use { st ->
            runCatching { st.execute("DROP TABLE IF EXISTS payment_perf_tmp") }
            st.execute("CREATE TABLE payment_perf_tmp LIKE payment")
            // CREATE TABLE LIKE 는 원본(payment)의 현재 인덱스 구성을 그대로 복제한다.
            // payment에 idx_number가 없는 현재는 withIdxNumber=true 일 때만 clone에 추가로 만들어준다.
            if (withIdxNumber) {
                runCatching { st.execute("ALTER TABLE payment_perf_tmp ADD INDEX idx_number (number)") }
            }
        }
        val insertSql = "INSERT INTO payment_perf_tmp " +
                "(id, reservation_id, number, price, status, payment_log, paid_at, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?)"
        val rowsToInsert = 20_000
        val (medianMs, samples) = ExplainSupport.timedExecuteMedianMs(repeats = 3, beforeEach = {
            conn.createStatement().use { it.execute("TRUNCATE TABLE payment_perf_tmp") }
        }) {
            conn.prepareStatement(insertSql).use { ps ->
                conn.autoCommit = false
                for (i in 1..rowsToInsert) {
                    ps.setLong(1, i.toLong())
                    ps.setLong(2, i.toLong())
                    ps.setString(3, "WP%014d".format(i))
                    ps.setInt(4, 10000)
                    ps.setString(5, "PAID")
                    ps.setString(6, "{\"k\":\"v\"}")
                    ps.setTimestamp(7, java.sql.Timestamp(System.currentTimeMillis()))
                    ps.setTimestamp(8, java.sql.Timestamp(System.currentTimeMillis()))
                    ps.setTimestamp(9, java.sql.Timestamp(System.currentTimeMillis()))
                    ps.addBatch()
                }
                ps.executeBatch()
                conn.commit()
                conn.autoCommit = true
            }
        }
        conn.createStatement().use { it.execute("DROP TABLE IF EXISTS payment_perf_tmp") }

        ReportWriter.writeRaw(
            "05b-payment-write-cost-$fileTag",
            "rowsToInsert=$rowsToInsert reps=3\n" + samples.mapIndexed { i, v -> "rep${i + 1}=${"%.1f".format(v)}ms" }
                .joinToString("\n") + "\nmedian=${"%.1f".format(medianMs)}ms"
        )
        ReportWriter.appendSummary(scenario, phase, "payment_perf_tmp", "INSERT", null, rowsToInsert.toLong(), null, null, medianMs, 3, "batch insert 2만건 wall-clock")
    }

    // ────────────────────────────────────────────────────────────────
    // 6) 회원 중복 인덱스 — member.uuid UNIQUE 제약과 idx_uuid
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("6. 회원 uuid 조회 — member.uuid UNIQUE vs idx_uuid 중복")
    fun memberUuidDuplicateIndex() {
        val sql = "SELECT * FROM member WHERE uuid = 'u-000000000777'"
        val scenario = "회원UUID중복인덱스(member.idx_uuid)"

        // Member 엔티티에서 idx_uuid를 이미 제거했으므로(이 보고서의 결정 사항) 스키마는
        // 기본적으로 "제거 후" 상태다. "있었을 때"는 거꾸로 임시로 추가해 재현한다.
        measureSelect(scenario, "제거후(현재코드, unique만 있음)", "after", sql, "06-member-uuid-read")
        ExplainSupport.execDdl(conn, "ALTER TABLE member ADD INDEX idx_uuid (uuid)")
        try {
            measureSelect(scenario, "제거전(unique+idx_uuid둘다있음, 가정)", "before", sql, "06-member-uuid-read")
        } finally {
            ExplainSupport.dropIndexIfExists(conn, "member", "idx_uuid")
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 7) 회차별 좌석 조회 — ORDER BY id filesort + 커버링 후보 인덱스
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("7. 회차별 좌석 조회 ORDER BY id — filesort 비용 + (schedule_id, id) 후보")
    fun seatOrderByIdFilesort() {
        val sql = "SELECT * FROM seat WHERE schedule_id = 5000 ORDER BY id"
        val scenario = "회차별좌석조회_filesort(seat 후보 idx_schedule_id_id)"

        measureSelect(scenario, "before(uk_schedule_id_number만=현재코드)", "before", sql, "07-seat-order-by-id")
        ExplainSupport.execDdl(conn, "ALTER TABLE seat ADD INDEX idx_schedule_id_id_candidate (schedule_id, id)")
        try {
            measureSelect(scenario, "after(schedule_id+id 후보인덱스추가)", "after", sql, "07-seat-order-by-id")
        } finally {
            ExplainSupport.dropIndexIfExists(conn, "seat", "idx_schedule_id_id_candidate")
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 8) 조건부 UPDATE 3종 — PK 사용 확인 + 실측 시간
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("8. 조건부 UPDATE 3종 — PK(id IN) 사용 확인 + wall-clock 실측")
    fun conditionalUpdates() {
        measureConditionalUpdate(
            scenario = "조건부UPDATE_holdAvailableSeats",
            explainSql = "UPDATE seat SET status='HOLD', reservation_id=999999 WHERE id IN (450001,450002,450003) AND status='AVAILABLE'",
            fileLabel = "08a-hold-available-seats",
            resetState = {
                conn.createStatement().use {
                    it.execute("UPDATE seat SET status='AVAILABLE', reservation_id=NULL WHERE id IN (450001,450002,450003)")
                }
            },
            execute = {
                conn.createStatement().use {
                    it.executeUpdate("UPDATE seat SET status='HOLD', reservation_id=999999 WHERE id IN (450001,450002,450003) AND status='AVAILABLE'")
                }
            }
        )

        measureConditionalUpdate(
            scenario = "조건부UPDATE_releaseHeldSeats",
            explainSql = "UPDATE seat SET status='AVAILABLE', reservation_id=NULL WHERE id IN (450004,450005,450006) AND status='HOLD'",
            fileLabel = "08b-release-held-seats",
            resetState = {
                conn.createStatement().use {
                    it.execute("UPDATE seat SET status='HOLD', reservation_id=999999 WHERE id IN (450004,450005,450006)")
                }
            },
            execute = {
                conn.createStatement().use {
                    it.executeUpdate("UPDATE seat SET status='AVAILABLE', reservation_id=NULL WHERE id IN (450004,450005,450006) AND status='HOLD'")
                }
            }
        )

        measureConditionalUpdate(
            scenario = "조건부UPDATE_confirmHeldSeats",
            explainSql = "UPDATE seat SET status='RESERVED' WHERE id IN (450007,450008,450009) AND status='HOLD' AND reservation_id=999999",
            fileLabel = "08c-confirm-held-seats",
            resetState = {
                conn.createStatement().use {
                    it.execute("UPDATE seat SET status='HOLD', reservation_id=999999 WHERE id IN (450007,450008,450009)")
                }
            },
            execute = {
                conn.createStatement().use {
                    it.executeUpdate("UPDATE seat SET status='RESERVED' WHERE id IN (450007,450008,450009) AND status='HOLD' AND reservation_id=999999")
                }
            }
        )

        // 측정에 사용한 더미 좌석(450001~450009)을 원래 시드 상태(AVAILABLE/예약 없음)로 복원한다.
        conn.createStatement().use {
            it.execute("UPDATE seat SET status='AVAILABLE', reservation_id=NULL WHERE id BETWEEN 450001 AND 450009")
        }
    }

    private fun measureConditionalUpdate(
        scenario: String,
        explainSql: String,
        fileLabel: String,
        resetState: () -> Unit,
        execute: () -> Unit
    ) {
        resetState()
        val plan = ExplainSupport.explainUpdate(conn, explainSql)
        val (medianMs, samples) = ExplainSupport.timedExecuteMedianMs(repeats = repeats, beforeEach = resetState, execute = execute)

        ReportWriter.writeRaw(
            fileLabel,
            "SQL: $explainSql\n\n--- EXPLAIN ---\n${ExplainSupport.planRowsToText(plan)}\n\n" +
                    "--- wall-clock 반복 ${repeats}회 (ms) ---\n" +
                    samples.mapIndexed { i, v -> "rep${i + 1}=${"%.6f".format(v)}ms" }.joinToString("\n") +
                    "\nmedian=${"%.6f".format(medianMs)}ms\n\n" +
                    "참고: MySQL 8.0.46 `EXPLAIN ANALYZE UPDATE ...` 는 " +
                    "\"not executable by iterator executor\" 로 거부되어 EXPLAIN(계획)만 수집하고 " +
                    "시간은 System.nanoTime() 으로 직접 측정했다 (실측 확인됨)."
        )
        val row = plan.firstOrNull()
        ReportWriter.appendSummary(scenario, "현재상태(PK 사용여부 확인)", row?.table, row?.type, row?.key, row?.rows, row?.filtered, row?.extra, medianMs, repeats)
    }

    // ────────────────────────────────────────────────────────────────
    // 9) 기타 현황 확인 (이미 최적 상태로 보이는 쿼리들) — EXPLAIN만, before/after 없음
    // ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("9. 기타 조회 현황 확인 (이미 leftmost prefix / PK로 최적화됨)")
    fun otherQueriesCurrentState() {
        val cases = listOf(
            "findByScheduleIdAndSeatIds" to "SELECT * FROM seat WHERE schedule_id = 5000 AND id IN (450001,450002,450003)",
            "findConcertDetailById" to ("SELECT c.id, c.title, c.location, MAX(s.viewing_time), MIN(s.concerted_at), MAX(s.concerted_at) " +
                    "FROM concert c LEFT JOIN schedules s ON c.id = s.concert_id WHERE c.id = 500 GROUP BY c.id, c.title, c.location"),
            "findByLoginId" to "SELECT * FROM member WHERE login_id = 'user00000777'",
            "point.findByMemberId" to "SELECT * FROM point WHERE member_id = 777"
        )
        val sb = StringBuilder()
        cases.forEach { (label, sql) ->
            val plan = ExplainSupport.explain(conn, sql)
            sb.appendLine("=== $label ===")
            sb.appendLine("SQL: $sql")
            sb.appendLine(ExplainSupport.planRowsToText(plan))
            sb.appendLine()
            val row = plan.firstOrNull()
            ReportWriter.appendSummary("기타현황_$label", "현재상태", row?.table, row?.type, row?.key, row?.rows, row?.filtered, row?.extra, null, 0, "before/after 비교 없음(이미 최적 인덱스 사용 확인용)")
        }
        ReportWriter.writeRaw("09-other-queries-current-state", sb.toString())
    }
}
