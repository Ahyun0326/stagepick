package kr.hhplus.be.server.domains.seat.domain.model

import kr.hhplus.be.server.domains.reservation.domain.model.Reservation

class Seat(
    val id: Long,
    val scheduleId: Long,
    var number: String,
    var status: String,
    var price: Int
) {

    var reservationId: Long = 0

    fun isAvailable(): Boolean {
        if (status != SeatStatus.AVAILABLE.name) {
            return false
        }

        return true
    }

    fun reserve(reservation: Reservation) {
        status = SeatStatus.HOLD.name
        reservationId = reservation.id
    }
}