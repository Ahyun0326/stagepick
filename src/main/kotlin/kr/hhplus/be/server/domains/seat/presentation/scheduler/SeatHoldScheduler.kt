package kr.hhplus.be.server.domains.seat.presentation.scheduler

import kr.hhplus.be.server.domains.seat.application.facade.SeatFacade
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@ConditionalOnProperty(
    prefix = "app.scheduler",
    name = ["enabled"],
    havingValue = "true"
)
@Component
class SeatHoldScheduler(
    private val seatFacade: SeatFacade
) {
    @Scheduled(fixedRate = 1000)
    fun expireSeatHold() {
        seatFacade.expireSeatHold()
    }
}