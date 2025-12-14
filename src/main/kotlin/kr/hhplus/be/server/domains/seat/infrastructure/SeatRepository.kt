package kr.hhplus.be.server.domains.seat.infrastructure

import kr.hhplus.be.server.domains.seat.domain.Seat
import org.springframework.data.jpa.repository.JpaRepository

interface SeatRepository : JpaRepository<Seat, Long> {
}