package kr.hhplus.be.server.domains.seat.infrastructure.persistence

import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import org.springframework.stereotype.Repository

@Repository
class SeatJpaRepository(
    private val springSeatJpa: SpringSeatJpa,
    private val seatMapper: SeatMapper
) : SeatRepository {
    override fun findSeatsByScheduleId(scheduleId: Long): List<Seat> {
//        val seatEntities = springSeatJpa.findAllByScheduleId(scheduleId)
//
//        return seatEntities.map {
//            seatMapper.toDomain(it)
//        }

        return springSeatJpa.findAllByScheduleId(scheduleId)
            .map { it.toDomain() }
    }

    override fun findSeats(scheduleId: Long, seatIds: List<Long>): List<Seat> {
        val seatEntities = springSeatJpa.findByScheduleIdAndSeatIds(scheduleId, seatIds)

        return seatEntities.map {
            seatMapper.toDomain(it)
        }
    }

    override fun saveAll(seats: List<Seat>) {
        val seatMap = seats.associateBy { it.id }

        val seatEntities = springSeatJpa.findSeatEntitiesByIdIn(seatMap.keys.toList())

        seatEntities.forEach { entity ->
            val seat = seatMap[entity.id]
            if (seat != null) {
                entity.updateFrom(seat)
            }
        }

        springSeatJpa.saveAll(seatEntities)
    }

    override fun updateStatusToAvailable(expiredSeatIds: List<Long>) {
        springSeatJpa.updateStatusToAvailable(expiredSeatIds, SeatStatus.AVAILABLE.name)
    }

    override fun findSeatsByReservationId(reservationId: Long): List<Seat> {
        val seatEntities = springSeatJpa.findAllByReservationId(reservationId)

        return seatEntities.map { seatMapper.toDomain(it) }
    }

    override fun updateStatusToReserved(seatIds: List<Long>) {
        springSeatJpa.updateStatusToReserved(seatIds, SeatStatus.RESERVED.name)
    }
}