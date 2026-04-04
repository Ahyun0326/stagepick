package kr.hhplus.be.server.domains.payment.domain.model

import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto
import java.time.LocalDateTime

data class PaymentLog(
    val username: String,
    val title: String,
    val location: String,
    val concertedAt: LocalDateTime,
    val viewingTime: Int,
    val seats: List<String>,
    val price: Int
) {
    companion object {
        fun create(reservationPaymentDetail: ReservationPaymentDetailQueryDto): PaymentLog {
            return PaymentLog(
                "김예서",
                reservationPaymentDetail.title,
                reservationPaymentDetail.location,
                reservationPaymentDetail.datetime,
                reservationPaymentDetail.viewingTime,
                reservationPaymentDetail.seatNumbers,
                reservationPaymentDetail.price
            )
        }
    }
}