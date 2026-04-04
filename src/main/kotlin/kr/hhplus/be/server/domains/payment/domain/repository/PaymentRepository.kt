package kr.hhplus.be.server.domains.payment.domain.repository

import kr.hhplus.be.server.domains.payment.domain.model.Payment
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation

interface PaymentRepository {
    fun findByReservationId(reservationId: Long): Payment?
    fun save(payment: Payment, reservation: Reservation): Payment
}