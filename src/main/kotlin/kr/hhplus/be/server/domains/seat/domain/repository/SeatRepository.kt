package kr.hhplus.be.server.domains.seat.domain.repository

import kr.hhplus.be.server.domains.seat.domain.model.Seat

interface SeatRepository {
    fun findSeatsByScheduleId(scheduleId: Long): List<Seat>
}