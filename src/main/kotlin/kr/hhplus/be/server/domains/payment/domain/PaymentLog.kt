package kr.hhplus.be.server.domains.payment.domain

import java.time.LocalDateTime

data class PaymentLog (
    val username: String,
    val title: String,
    val location: String,
    val concertedAt: LocalDateTime,
    val viewingTime: Int,
    val seats: List<String>
)