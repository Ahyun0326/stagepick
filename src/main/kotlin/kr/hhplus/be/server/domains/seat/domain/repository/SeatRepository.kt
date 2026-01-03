package kr.hhplus.be.server.domains.seat.domain.repository

import kr.hhplus.be.server.domains.seat.domain.model.Seat

interface SeatRepository {
    fun findSeatsByScheduleId(scheduleId: Long): List<Seat>
    fun findSeats(scheduleId: Long, seatIds: List<Long>): List<Seat>
    fun saveAll(seats: List<Seat>)
    fun updateStatusToAvailable(expiredSeatIds: List<Long>)
}