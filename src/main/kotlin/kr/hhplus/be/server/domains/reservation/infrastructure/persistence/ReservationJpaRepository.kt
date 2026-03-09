package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto
import org.springframework.data.repository.findByIdOrNull
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

    override fun findById(reservationId: Long): Reservation? {
        val reservationEntity = springReservationJpa.findByIdOrNull(reservationId)

        return reservationEntity?.let {
            reservationMapper.toDomain(it)
        }
    }

    override fun getWithDetailsById(reservationId: Long): ReservationPaymentDetailQueryDto? {
        return springReservationJpa.getWithDetailsById(reservationId)
    }

}