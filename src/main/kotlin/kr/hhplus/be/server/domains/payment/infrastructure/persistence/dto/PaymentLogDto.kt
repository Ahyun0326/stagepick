package kr.hhplus.be.server.domains.payment.infrastructure.persistence.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kr.hhplus.be.server.domains.payment.domain.model.PaymentLog
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentLogDto(
    val username: String,
    val title: String,
    val location: String,
    val concertedAt: LocalDateTime,
    val viewingTime: Int,
    val seats: List<String>,
    val price: Int
) {

    fun toDomain(): PaymentLog {
        return PaymentLog(
            username = username,
            title = title,
            location = location,
            concertedAt = concertedAt,
            viewingTime = viewingTime,
            seats = seats,
            price = price
        )
    }

    companion object {
        fun from(domain: PaymentLog): PaymentLogDto {
            return PaymentLogDto(
                username = domain.username,
                title = domain.title,
                location = domain.location,
                concertedAt = domain.concertedAt,
                viewingTime = domain.viewingTime,
                seats = domain.seats,
                price = domain.price
            )
        }
    }
}