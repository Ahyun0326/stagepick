package kr.hhplus.be.server.domains.reservation.domain.repository.dto

import java.time.LocalDateTime

data class ReservationPaymentDetailQueryDto(
    val reservationId: Long,
    val title: String,
    val location: String,
    val viewingTime: Int,
    val datetime: LocalDateTime,
    val seatNumbers: List<String>,
    val price: Int
) {}