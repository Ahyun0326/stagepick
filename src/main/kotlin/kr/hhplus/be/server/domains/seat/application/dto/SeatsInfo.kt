package kr.hhplus.be.server.domains.seat.application.dto

import kr.hhplus.be.server.domains.seat.domain.model.Seat

data class SeatsInfo(
    val seats: List<SeatInfo>
) {
    companion object {
        fun from(seats: List<Seat>): SeatsInfo {
            return SeatsInfo(
                seats.map { SeatInfo.from(it) }
            )
        }
    }

}