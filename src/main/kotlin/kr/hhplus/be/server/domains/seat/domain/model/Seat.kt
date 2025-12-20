package kr.hhplus.be.server.domains.seat.domain.model

class Seat(
    val id: Long,
    val scheduleId: Long,
    var reservationId: Long?,
    var number: String,
    var status: String,
    var price: Int
) {}