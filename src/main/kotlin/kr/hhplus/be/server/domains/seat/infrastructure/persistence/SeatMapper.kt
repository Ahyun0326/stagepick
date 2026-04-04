package kr.hhplus.be.server.domains.seat.infrastructure.persistence

import kr.hhplus.be.server.domains.seat.domain.model.Seat
import org.springframework.stereotype.Component

@Component
class SeatMapper {

    fun toDomain(entity: SeatEntity): Seat {
        return Seat(
            id = entity.id,
            scheduleId = entity.schedule.id,
            number = entity.number,
            status = entity.status,
            price = entity.price
        )
    }
}

fun SeatEntity.toDomain(): Seat= Seat(
    id = this.id,
    scheduleId = this.schedule.id,
    number = this.number,
    status = this.status,
    price = this.price
)