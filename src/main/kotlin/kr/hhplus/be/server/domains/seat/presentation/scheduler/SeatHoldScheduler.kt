package kr.hhplus.be.server.domains.seat.presentation.scheduler

import kr.hhplus.be.server.domains.seat.application.facade.SeatFacade
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SeatHoldScheduler(
    private val seatFacade: SeatFacade
) {
    @Scheduled(fixedRate = 1000)
    fun expireSeatHold() {
        seatFacade.expireSeatHold()
    }
}