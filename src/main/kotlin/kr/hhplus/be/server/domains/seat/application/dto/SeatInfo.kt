package kr.hhplus.be.server.domains.seat.application.dto

import kr.hhplus.be.server.domains.seat.domain.model.Seat

data class SeatInfo(
    val seatId: Long,
    val number: String,
    val status: String,
    val price: Int
) {
    companion object {
        fun of(seat: Seat, status: String): SeatInfo {
            return SeatInfo(
                seat.id,
                seat.number,
                status,
                seat.price
            )
        }
    }
}