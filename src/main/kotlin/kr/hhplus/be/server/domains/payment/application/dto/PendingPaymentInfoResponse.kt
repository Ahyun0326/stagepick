package kr.hhplus.be.server.domains.payment.application.dto

import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto
import java.time.LocalDateTime

data class PendingPaymentInfoResponse(
    val reservationId: Long,
    val title: String,
    val viewingTime: Int,
    val datetime: LocalDateTime,
    val seatNumbers: List<String>,
    val price: Int
) {
    companion object {
        fun from(reservationPaymentDetailQueryDto: ReservationPaymentDetailQueryDto): PendingPaymentInfoResponse {
            return PendingPaymentInfoResponse(
                reservationPaymentDetailQueryDto.reservationId,
                reservationPaymentDetailQueryDto.title,
                reservationPaymentDetailQueryDto.viewingTime,
                reservationPaymentDetailQueryDto.datetime,
                reservationPaymentDetailQueryDto.seatNumbers,
                reservationPaymentDetailQueryDto.price
            )
        }
    }

}