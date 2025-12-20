package kr.hhplus.be.server.domains.seat.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringSeatJpa : JpaRepository<SeatEntity, Long> {

    @Query("select s from SeatEntity s " +
            "where s.schedule.id in :scheduleId order by s.id")
    fun findSeatEntityByScheduleId(@Param("scheduleId") scheduleId: Long): List<SeatEntity>
}