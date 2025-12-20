package kr.hhplus.be.server.domains.seat.infrastructure.persistence

import kr.hhplus.be.server.domains.seat.domain.model.Seat
import org.springframework.stereotype.Component

@Component
class SeatMapper {

    fun toDomain(entity: SeatEntity): Seat {
        return Seat(
            id = entity.id,
            scheduleId = entity.schedule.id,
            reservationId = entity.reservation?.id,
            number = entity.number,
            status = entity.status,
            price = entity.price
        )
    }
}