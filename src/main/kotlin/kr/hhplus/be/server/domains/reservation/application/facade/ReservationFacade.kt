package kr.hhplus.be.server.domains.reservation.application.facade

import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationResponse
import kr.hhplus.be.server.domains.reservation.application.usecase.ReserveSeatService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ReservationFacade(
    private val reserveSeatService: ReserveSeatService
) {

    @Transactional
    fun reserveSeat(reservationRequest: ReservationRequest): ReservationResponse {
        return reserveSeatService.invoke(reservationRequest)
    }
}