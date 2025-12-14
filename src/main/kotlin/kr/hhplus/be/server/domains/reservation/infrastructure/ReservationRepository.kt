package kr.hhplus.be.server.domains.reservation.infrastructure

import kr.hhplus.be.server.domains.reservation.domain.Reservation
import org.springframework.data.jpa.repository.JpaRepository

interface ReservationRepository : JpaRepository<Reservation, Long> {
}