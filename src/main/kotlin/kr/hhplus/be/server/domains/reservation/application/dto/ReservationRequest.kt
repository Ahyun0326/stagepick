package kr.hhplus.be.server.domains.reservation.application.dto

data class ReservationRequest(
    val scheduleId: Long,
    val seatIds: List<Long>
) {}