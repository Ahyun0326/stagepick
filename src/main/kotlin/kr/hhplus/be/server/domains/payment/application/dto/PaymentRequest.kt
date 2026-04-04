package kr.hhplus.be.server.domains.payment.application.dto

data class PaymentRequest(
    val reservationId: Long,
    val point: Int
) {
}