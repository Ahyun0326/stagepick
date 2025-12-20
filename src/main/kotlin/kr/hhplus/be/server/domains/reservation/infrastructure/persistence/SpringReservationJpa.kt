package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SpringReservationJpa : JpaRepository<ReservationEntity, Long> {
}