package kr.hhplus.be.server.domains.payment.application.dto

import kr.hhplus.be.server.domains.payment.domain.model.Payment
import java.time.LocalDateTime

data class PaymentResponse(
    val paymentId: Long,
    val paymentNumber: String,
    val amount: Int,
    val status: String,
    val paidAt: LocalDateTime?
) {

    companion object {
        fun from(payment: Payment): PaymentResponse {
            return PaymentResponse(
                payment.id,
                payment.number,
                payment.price,
                payment.status,
                payment.createdAt
            )
        }
    }
}