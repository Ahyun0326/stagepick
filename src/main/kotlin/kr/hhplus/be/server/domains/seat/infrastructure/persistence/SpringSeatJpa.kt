package kr.hhplus.be.server.domains.seat.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringSeatJpa : JpaRepository<SeatEntity, Long> {

    @Query(
        "select s from SeatEntity s " +
                "where s.schedule.id in :scheduleId order by s.id"
    )
    fun findAllByScheduleId(@Param("scheduleId") scheduleId: Long): List<SeatEntity>


    @Query(
        "select s from SeatEntity s " +
                "where s.schedule.id in :scheduleId and s.id in :seatIds"
    )
    fun findByScheduleIdAndSeatIds(
        @Param("scheduleId") scheduleId: Long,
        @Param("seatIds") seatIds: List<Long>
    ): List<SeatEntity>

    fun findSeatEntitiesByIdIn(ids: List<Long>): MutableList<SeatEntity>

    // 조건부 UPDATE: 분산락이 뚫려 동시에 들어와도 AVAILABLE 을 먼저 바꾼 한 트랜잭션만 성공 (나머지 0건)
    @Modifying(clearAutomatically = true)
    @Query(
        "update SeatEntity s set s.status = :hold, s.reservationId = :reservationId " +
                "where s.id in :ids and s.status = :available"
    )
    fun holdAvailableSeats(
        @Param("ids") ids: List<Long>,
        @Param("reservationId") reservationId: Long,
        @Param("hold") hold: String,
        @Param("available") available: String
    ): Int

    // 조건부 UPDATE: 결제로 이미 RESERVED 가 된 좌석을 만료 처리가 되돌리지 않도록 HOLD 일 때만 해제
    @Modifying(clearAutomatically = true)
    @Query(
        "update SeatEntity s set s.status = :available, s.reservationId = null " +
                "where s.id in :ids and s.status = :hold"
    )
    fun releaseHeldSeats(
        @Param("ids") ids: List<Long>,
        @Param("available") available: String,
        @Param("hold") hold: String
    ): Int

    fun findAllByReservationId(reservationId: Long): List<SeatEntity>

    // 조건부 UPDATE: 확인과 쓰기 사이에 만료 처리가 먼저 해제했다면 0건 → 예약 없는 RESERVED 좌석이 생기지 않는다
    @Modifying(clearAutomatically = true)
    @Query(
        "update SeatEntity s set s.status = :reserved " +
                "where s.id in :ids and s.status = :hold and s.reservationId = :reservationId"
    )
    fun confirmHeldSeats(
        @Param("ids") ids: List<Long>,
        @Param("reservationId") reservationId: Long,
        @Param("reserved") reserved: String,
        @Param("hold") hold: String
    ): Int

}