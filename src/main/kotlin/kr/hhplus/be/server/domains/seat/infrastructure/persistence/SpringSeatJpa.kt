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

    @Modifying(clearAutomatically = true)
    @Query("update SeatEntity s set s.status = :status where s.id in :ids")
    fun updateStatusToAvailable(@Param("ids") expiredSeatIds: List<Long>, @Param("status") status: String)

}