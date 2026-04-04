package kr.hhplus.be.server.domains.payment.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

import java.util.Optional

interface SpringPaymentJpa : JpaRepository<PaymentEntity, Long> {
    fun findByReservationId(reservationId: Long): Optional<PaymentEntity>
}