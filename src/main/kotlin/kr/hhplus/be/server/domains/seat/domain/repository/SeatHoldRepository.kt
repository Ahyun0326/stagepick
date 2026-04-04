package kr.hhplus.be.server.domains.seat.domain.repository

import kr.hhplus.be.server.domains.seat.domain.model.Seat

interface SeatHoldRepository {
    fun hold(seats: List<Seat>)
    fun getHeldStatus(seatIds: List<Long>): List<Boolean>
    fun getExpiredSeats(): List<Long>
    fun removeSeats(expiredSeatIds: List<Long>)
}