package kr.hhplus.be.server.domains.payment.domain.model

import io.hypersistence.tsid.TSID
import java.time.LocalDateTime

class Payment(
    val reservationId: Long,
    val number: String,
    var price: Int,
    var status: String,
    val paymentLog: PaymentLog,
    var createdAt: LocalDateTime? = null
) {
    var id: Long = 0L

    companion object {
        fun create(reservationId: Long, price: Int, paymentLog: PaymentLog): Payment {
            return Payment(
                reservationId = reservationId,
                number = TSID.fast().toLowerCase(),
                price = price,
                status = PaymentStatus.PAID.name,
                paymentLog = paymentLog,
            )
        }
    }

    fun assignId(id: Long) {
        this.id = id
    }

}