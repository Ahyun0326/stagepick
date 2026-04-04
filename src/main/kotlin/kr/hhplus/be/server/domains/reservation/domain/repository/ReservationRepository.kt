package kr.hhplus.be.server.domains.reservation.domain.repository

import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto

interface ReservationRepository {
    fun save(reservation: Reservation): Reservation
    fun findById(reservationId: Long): Reservation?
    fun getWithDetailsById(reservationId: Long): ReservationPaymentDetailQueryDto?
}