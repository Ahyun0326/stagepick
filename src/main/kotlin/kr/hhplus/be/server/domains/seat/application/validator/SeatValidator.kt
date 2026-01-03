package kr.hhplus.be.server.domains.seat.application.validator

import kr.hhplus.be.server.common.exception.SeatNotFoundException
import kr.hhplus.be.server.common.exception.SeatUnavailableException
import org.springframework.stereotype.Component

@Component
class SeatValidator {
    fun validateExistingSeats(existingIds: List<Long>, requestSeatIds: List<Long>) {
        val missingIds = requestSeatIds - existingIds.toSet()

        if (missingIds.isNotEmpty()) {
            throw SeatNotFoundException(missingIds)
        }
    }

    fun validateAvailableSeats(seatIds: List<Long>) {
        if (seatIds.isNotEmpty()) {
            throw SeatUnavailableException(seatIds)
        }
    }
}