package kr.hhplus.be.server.domains.reservation.interfaces.web

import kr.hhplus.be.server.common.response.ApiResponse
import kr.hhplus.be.server.domains.seat.application.dto.SeatsInfo
import kr.hhplus.be.server.domains.seat.application.facade.SeatFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reservation")
class ReservationController(
    private val seatFacade: SeatFacade,
) {

    @GetMapping("/seats/{scheduleId}")
    fun getSeatsInfo(@PathVariable("scheduleId") scheduleId: Long): ApiResponse<SeatsInfo> {
        return ApiResponse.success(seatFacade.getSeatsInfo(scheduleId))
    }
}