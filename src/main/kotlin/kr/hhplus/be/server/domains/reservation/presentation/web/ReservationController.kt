package kr.hhplus.be.server.domains.reservation.presentation.web

import kr.hhplus.be.server.common.response.ApiResponse
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationResponse
import kr.hhplus.be.server.domains.reservation.application.facade.ReservationFacade
import kr.hhplus.be.server.domains.seat.application.dto.SeatsInfo
import kr.hhplus.be.server.domains.seat.application.facade.SeatFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reservation/seats")
class ReservationController(
    private val seatFacade: SeatFacade,
    private val reservationFacade: ReservationFacade
) {

    @GetMapping("/{scheduleId}")
    fun getSeatsInfo(@PathVariable("scheduleId") scheduleId: Long): ApiResponse<SeatsInfo> {
        return ApiResponse.Companion.success(seatFacade.getSeatsInfo(scheduleId))
    }

    @PostMapping
    fun reserveSeat(@RequestBody reservationRequest: ReservationRequest): ApiResponse<ReservationResponse> {
        return ApiResponse.Companion.success(reservationFacade.reserveSeat(reservationRequest))
    }
}