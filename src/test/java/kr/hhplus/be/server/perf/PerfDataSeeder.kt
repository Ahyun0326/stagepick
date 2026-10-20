package kr.hhplus.be.server.perf

import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 인덱스 성능 측정용 대량 데이터 시더.
 *
 * - Hibernate/JPA를 거치지 않고 순수 JDBC batch INSERT로 적재한다 (IDENTITY 전략은 JDBC
 *   배치만으로는 거의 가속이 안 되므로, PK를 직접 지정하고 `rewriteBatchedStatements=true`로
 *   다중 VALUES INSERT로 재작성되게 한다 — [PerfJdbc] 참고).
 * - FK가 실제 DB 제약으로 걸린 체인(schedules→concert, seat→schedules, payment→reservation)은
 *   생성 순서를 지켜야 하므로 concert → schedules → seat → member → reservation → payment →
 *   point → point_history 순서로 적재한다.
 * - 규모는 과제 예시(좌석 50만 등)를 그대로 따르되, 디스크 여유(~11GB) 안전을 위해
 *   point_history 는 "수십만" 하한선인 30만으로 설정했다. 실제 적재된 건수는
 *   [Scale]에 상수로 남겨, 보고서가 이 값을 그대로 인용한다.
 */
object PerfDataSeeder {

    object Scale {
        const val CONCERT = 1_000
        const val SCHEDULE = 10_000
        const val SEAT = 500_000
        const val MEMBER = 100_000
        const val RESERVATION = 200_000
        const val PAYMENT = 100_000
        const val POINT = 100_000
        const val POINT_HISTORY = 300_000
    }

    private const val BATCH_SIZE = 2_000

    /** 이미 목표 규모만큼 적재돼 있으면 재시딩을 건너뛴다 (perfTest 반복 실행 시 시간 절약). */
    fun ensureSeeded(conn: Connection) {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM seat").use { rs ->
                rs.next()
                if (rs.getLong(1) == Scale.SEAT.toLong()) {
                    println("[PerfDataSeeder] 이미 목표 규모(seat=${Scale.SEAT})만큼 적재되어 있어 재시딩을 건너뜁니다.")
                    return
                }
            }
        }
        truncateAll(conn)
        seedAll(conn)
    }

    private fun truncateAll(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute("SET FOREIGN_KEY_CHECKS = 0")
            listOf("point_history", "point", "payment", "reservation", "seat", "schedules", "member", "concert")
                .forEach { st.execute("TRUNCATE TABLE `$it`") }
            st.execute("SET FOREIGN_KEY_CHECKS = 1")
        }
    }

    private fun seedAll(conn: Connection) {
        val wasAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            val t0 = System.currentTimeMillis()
            seedConcerts(conn)
            seedSchedules(conn)
            seedSeats(conn)
            seedMembers(conn)
            seedReservations(conn)
            seedPayments(conn)
            seedPoints(conn)
            seedPointHistories(conn)
            println("[PerfDataSeeder] 전체 시딩 완료: ${System.currentTimeMillis() - t0}ms")
        } finally {
            conn.autoCommit = wasAutoCommit
        }
    }

    private fun now() = Timestamp.valueOf(LocalDateTime.now())

    private fun seedConcerts(conn: Connection) {
        val sql = "INSERT INTO concert (id, title, location, created_at, updated_at) VALUES (?,?,?,?,?)"
        conn.prepareStatement(sql).use { ps ->
            batchInsert(conn, ps, 1..Scale.CONCERT) { id ->
                ps.setLong(1, id.toLong())
                ps.setString(2, "콘서트 #$id")
                ps.setString(3, "장소 #${id % 50}")
                ps.setTimestamp(4, now())
                ps.setTimestamp(5, now())
            }
        }
    }

    /** 상위 20% 콘서트(인기 콘서트)에 회차를 더 많이 몰아 현실적인 쏠림을 재현한다. */
    private fun seedSchedules(conn: Connection) {
        val sql = "INSERT INTO schedules (schedule_id, concert_id, concerted_at, viewing_time, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?)"
        val popularConcertCount = (Scale.CONCERT * 0.2).toInt()
        val schedulesPerPopular = 30
        val popularTotal = popularConcertCount * schedulesPerPopular
        val remainingConcerts = Scale.CONCERT - popularConcertCount
        val remainingSchedules = Scale.SCHEDULE - popularTotal
        val schedulesPerNormal = remainingSchedules / remainingConcerts

        var scheduleId = 1L
        conn.prepareStatement(sql).use { ps ->
            val ids = 1..Scale.SCHEDULE
            batchInsert(conn, ps, ids) { _ ->
                val concertId: Long
                if (scheduleId <= popularTotal) {
                    concertId = ((scheduleId - 1) / schedulesPerPopular) + 1
                } else {
                    val offset = scheduleId - popularTotal - 1
                    concertId = popularConcertCount + (offset / schedulesPerNormal) + 1
                }
                val cappedConcertId = concertId.coerceAtMost(Scale.CONCERT.toLong())
                ps.setLong(1, scheduleId)
                ps.setLong(2, cappedConcertId)
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().plusDays(scheduleId % 365).withHour(19).withMinute(0).withSecond(0).withNano(0)))
                ps.setInt(4, 90 + (scheduleId % 3).toInt() * 30)
                ps.setTimestamp(5, now())
                ps.setTimestamp(6, now())
                scheduleId++
            }
        }
    }

    /** 회차당 50석(A1~E10), 좌측 60%는 RESERVED(예약 매핑), 우측 40%는 AVAILABLE로 남긴다. */
    private fun seedSeats(conn: Connection) {
        val sql = "INSERT INTO seat (id, schedule_id, reservation_id, number, status, price, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?)"
        val seatsPerSchedule = Scale.SEAT / Scale.SCHEDULE
        val reservedCutoff = (Scale.SEAT * 0.6).toLong() // seat.id <= cutoff 는 RESERVED
        val seatsPerReservation = 2L
        val maxReservationForSeats = Scale.RESERVATION * 3L / 4 // 상위 75% 예약까지만 좌석을 매핑

        conn.prepareStatement(sql).use { ps ->
            batchInsert(conn, ps, 1..Scale.SEAT) { seatId ->
                val lid = seatId.toLong()
                val scheduleId = ((lid - 1) / seatsPerSchedule) + 1
                val seatIndexInSchedule = ((lid - 1) % seatsPerSchedule).toInt()
                val row = ('A' + (seatIndexInSchedule / 10))
                val col = (seatIndexInSchedule % 10) + 1
                val number = "$row$col"

                val reservationId: Long?
                val status: String
                if (lid <= reservedCutoff) {
                    val candidate = ((lid - 1) / seatsPerReservation) + 1
                    if (candidate <= maxReservationForSeats) {
                        reservationId = candidate
                        status = "RESERVED"
                    } else {
                        reservationId = null
                        status = "AVAILABLE"
                    }
                } else {
                    reservationId = null
                    status = "AVAILABLE"
                }

                ps.setLong(1, lid)
                ps.setLong(2, scheduleId)
                if (reservationId != null) ps.setLong(3, reservationId) else ps.setNull(3, java.sql.Types.BIGINT)
                ps.setString(4, number)
                ps.setString(5, status)
                ps.setInt(6, 50_000 + (seatIndexInSchedule % 5) * 10_000)
                ps.setTimestamp(7, now())
                ps.setTimestamp(8, now())
            }
        }
    }

    private fun seedMembers(conn: Connection) {
        val sql = "INSERT INTO member (id, login_id, uuid, password, username, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?)"
        conn.prepareStatement(sql).use { ps ->
            batchInsert(conn, ps, 1..Scale.MEMBER) { id ->
                ps.setLong(1, id.toLong())
                ps.setString(2, "user%08d".format(id))
                ps.setString(3, "u-%012d".format(id))
                ps.setString(4, "{bcrypt}dummy")
                ps.setString(5, "회원$id")
                ps.setTimestamp(6, now())
                ps.setTimestamp(7, now())
            }
        }
    }

    private fun seedReservations(conn: Connection) {
        val sql = "INSERT INTO reservation (id, member_id, number, created_at, updated_at) VALUES (?,?,?,?,?)"
        conn.prepareStatement(sql).use { ps ->
            batchInsert(conn, ps, 1..Scale.RESERVATION) { id ->
                val lid = id.toLong()
                val memberId = ((lid - 1) % Scale.MEMBER) + 1
                ps.setLong(1, lid)
                ps.setLong(2, memberId)
                ps.setString(3, "R%014d".format(lid))
                ps.setTimestamp(4, now())
                ps.setTimestamp(5, now())
            }
        }
    }

    /** 예약의 상위 절반(ID가 작은 쪽)만 결제까지 완료된 것으로 간주한다. */
    private fun seedPayments(conn: Connection) {
        val sql = "INSERT INTO payment (id, reservation_id, number, price, status, payment_log, paid_at, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?)"
        conn.prepareStatement(sql).use { ps ->
            batchInsert(conn, ps, 1..Scale.PAYMENT) { id ->
                val lid = id.toLong()
                ps.setLong(1, lid)
                ps.setLong(2, lid) // reservation_id == id (상위 PAYMENT건 예약에 1:1 매핑)
                ps.setString(3, "P%014d".format(lid))
                ps.setInt(4, 100_000)
                ps.setString(5, "PAID")
                ps.setString(
                    6,
                    """{"username":"회원$lid","title":"콘서트","location":"장소","concertedAt":"2026-01-01T19:00:00","viewingTime":120,"seats":["A1","A2"],"price":100000}"""
                )
                ps.setTimestamp(7, now())
                ps.setTimestamp(8, now())
                ps.setTimestamp(9, now())
            }
        }
    }

    private fun seedPoints(conn: Connection) {
        val sql = "INSERT INTO point (id, member_id, point, created_at, updated_at) VALUES (?,?,?,?,?)"
        conn.prepareStatement(sql).use { ps ->
            batchInsert(conn, ps, 1..Scale.POINT) { id ->
                val lid = id.toLong()
                ps.setLong(1, lid)
                ps.setLong(2, lid) // member_id == id (1인 1포인트 계좌)
                ps.setInt(3, 10_000 + (id % 50) * 1_000)
                ps.setTimestamp(4, now())
                ps.setTimestamp(5, now())
            }
        }
    }

    /** 회원당 3건의 포인트 내역(충전/사용 교대)을 생성한다. */
    private fun seedPointHistories(conn: Connection) {
        val sql = "INSERT INTO point_history (id, member_id, current_point, changed_point, type, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?)"
        val perMember = Scale.POINT_HISTORY / Scale.MEMBER
        conn.prepareStatement(sql).use { ps ->
            batchInsert(conn, ps, 1..Scale.POINT_HISTORY) { id ->
                val lid = id.toLong()
                val memberId = ((lid - 1) / perMember) + 1
                val seq = (lid - 1) % perMember
                val type = if (seq % 2 == 0L) "CHARGE" else "USE"
                ps.setLong(1, lid)
                ps.setLong(2, memberId)
                ps.setInt(3, 10_000)
                ps.setInt(4, 1_000)
                ps.setString(5, type)
                ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now().minusMinutes(seq)))
                ps.setTimestamp(7, now())
            }
        }
    }

    private inline fun batchInsert(
        conn: Connection,
        ps: java.sql.PreparedStatement,
        range: IntRange,
        bind: (Int) -> Unit
    ) {
        var count = 0
        for (id in range) {
            bind(id)
            ps.addBatch()
            count++
            if (count % BATCH_SIZE == 0) {
                ps.executeBatch()
                conn.commit()
            }
        }
        ps.executeBatch()
        conn.commit()
    }
}
