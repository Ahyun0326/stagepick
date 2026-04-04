package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import org.springframework.stereotype.Component

@Component
class ReservationMapper {
    fun toEntity(reservation: Reservation): ReservationEntity {
        return ReservationEntity(
            memberId = reservation.memberId,
            number = reservation.number
        )
    }

    fun toDomain(entity: ReservationEntity): Reservation {
        val reservation = Reservation(entity.number, entity.memberId)
        reservation.assignId(entity.id)
        return reservation
    }

}