package kr.hhplus.be.server.domains.seat.infrastructure.config

import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.application.usecase.GetSeatsInfoService
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SeatUseCaseConfig {

    @Bean
    fun getAvailableSeatsService(
        seatRepository: SeatRepository,
        scheduleRepository: ScheduleRepository
    ): GetSeatsInfoService {
        return GetSeatsInfoService(seatRepository, scheduleRepository)
    }
}