package kr.hhplus.be.server.domains.point.infrastructure.config

import kr.hhplus.be.server.domains.point.application.usecase.ChargePointService
import kr.hhplus.be.server.domains.point.application.usecase.FindPointService
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PointUseCaseConfig {

    @Bean
    fun chargePointService(
        pointRepository: PointRepository,
        pointHistoryRepository: PointHistoryRepository,
        pointValidator: PointValidator
    ): ChargePointService {
        return ChargePointService(
            pointRepository = pointRepository,
            pointHistoryRepository = pointHistoryRepository,
            pointValidator = pointValidator
        )
    }

    @Bean
    fun findPointService(
        pointRepository: PointRepository
    ): FindPointService {
        return FindPointService(pointRepository)
    }
}
