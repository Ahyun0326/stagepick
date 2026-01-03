package kr.hhplus.be.server.domains.seat.application.usecase

import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class ExpireSeatHoldService(
    private val seatRepository: SeatRepository,
    private val seatHoldRepository: SeatHoldRepository
) {
    fun processExpiredSeats() {
        val expiredSeatIds = seatHoldRepository.getExpiredSeats()

        if (expiredSeatIds.isNotEmpty()) {
            seatRepository.updateStatusToAvailable(expiredSeatIds)
            seatHoldRepository.removeSeats(expiredSeatIds)
        }
    }
}