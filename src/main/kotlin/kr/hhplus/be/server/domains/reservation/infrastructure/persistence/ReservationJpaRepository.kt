package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import org.springframework.stereotype.Repository

@Repository
class ReservationJpaRepository(
    private val springReservationJpa: SpringReservationJpa,
    private val reservationMapper: ReservationMapper
) : ReservationRepository {
    override fun save(reservation: Reservation): Reservation {
        return reservationMapper.toDomain(
            springReservationJpa.save(reservationMapper.toEntity(reservation))
        )
    }

}