package kr.hhplus.be.server.domains.reservation.infrastructure.config

import kr.hhplus.be.server.domains.common.lock.LockManager
import kr.hhplus.be.server.domains.reservation.application.usecase.ReserveSeatService
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.application.validator.SeatValidator
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReservationUseCaseConfig {

    @Bean
    fun placeReservationService(
        scheduleRepository: ScheduleRepository,
        seatRepository: SeatRepository,
        reservationRepository: ReservationRepository,
        seatHoldRepository: SeatHoldRepository,
        seatValidator: SeatValidator,
        lockManager: LockManager
    ): ReserveSeatService {
        return ReserveSeatService(
            scheduleRepository,
            seatRepository,
            reservationRepository,
            seatHoldRepository,
            seatValidator,
            lockManager
        )
    }
}