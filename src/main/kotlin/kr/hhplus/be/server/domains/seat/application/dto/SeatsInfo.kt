package kr.hhplus.be.server.domains.seat.application.dto

import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus

data class SeatsInfo(
    val seats: List<SeatInfo>
) {
    companion object {
        fun of(seats: List<Seat>, heldStatuses: List<Boolean>): SeatsInfo {
            return SeatsInfo(
                seats.zip(heldStatuses).map { (seat, isHeldInStatus) ->
                    val finalStatus = when {
                        isHeldInStatus -> SeatStatus.HOLD.name
                        else -> SeatStatus.AVAILABLE.name
                    }

                    SeatInfo.of(seat, finalStatus)
                }
            )
        }
    }

}