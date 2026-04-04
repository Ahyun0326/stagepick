package kr.hhplus.be.server.domains.payment.infrastructure.persistence

import kr.hhplus.be.server.domains.payment.domain.model.Payment
import kr.hhplus.be.server.domains.payment.domain.repository.PaymentRepository
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.infrastructure.persistence.SpringReservationJpa
import org.springframework.stereotype.Repository

@Repository
class PaymentJpaRepository(
    private val springPaymentJpa: SpringPaymentJpa,
    private val springReservationJpa: SpringReservationJpa,
    private val paymentMapper: PaymentMapper,
): PaymentRepository {

    override fun findByReservationId(reservationId: Long): Payment? {
        return springPaymentJpa.findByReservationId(reservationId)
            .map { paymentMapper.toDomain(it) }
            .orElse(null)
    }

    override fun save(payment: Payment, reservation: Reservation): Payment {
        val reservationEntity = springReservationJpa.getReferenceById(reservation.id)
        val paymentEntity = paymentMapper.toEntity(payment, reservationEntity)

        return paymentMapper.toDomain(springPaymentJpa.save(paymentEntity))
    }

}