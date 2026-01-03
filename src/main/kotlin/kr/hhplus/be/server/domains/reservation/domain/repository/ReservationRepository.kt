package kr.hhplus.be.server.domains.reservation.domain.repository

import kr.hhplus.be.server.domains.reservation.domain.model.Reservation

interface ReservationRepository {
    fun save(reservation: Reservation): Reservation
}