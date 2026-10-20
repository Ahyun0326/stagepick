package kr.hhplus.be.server.domains.seat.domain.model

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
}