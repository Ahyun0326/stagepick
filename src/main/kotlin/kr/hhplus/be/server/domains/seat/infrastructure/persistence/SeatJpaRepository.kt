package kr.hhplus.be.server.domains.seat.infrastructure.persistence

import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import org.springframework.stereotype.Repository

@Repository
class SeatJpaRepository(
    private val springSeatJpa: SpringSeatJpa,
    private val seatMapper: SeatMapper
): SeatRepository {
    override fun findSeatsByScheduleId(scheduleId: Long): List<Seat> {
        val seatEntities = springSeatJpa.findSeatEntityByScheduleId(scheduleId)

        return seatEntities.map {
            seatMapper.toDomain(it)
        }
    }
}