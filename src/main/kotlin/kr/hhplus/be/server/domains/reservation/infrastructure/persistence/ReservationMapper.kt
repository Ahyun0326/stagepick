package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import org.springframework.stereotype.Component

@Component
class ReservationMapper {
    fun toEntity(reservation: Reservation): ReservationEntity {
        return ReservationEntity(
            number = reservation.number
        )
    }

    fun toDomain(entity: ReservationEntity): Reservation {
        val reservation = Reservation(entity.number)
        reservation.assignId(entity.id)
        return reservation
    }

}