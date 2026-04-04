package kr.hhplus.be.server.domains.payment.infrastructure.config

import kr.hhplus.be.server.domains.common.lock.LockManager
import kr.hhplus.be.server.domains.payment.application.usecase.FindPendingPaymentInfoService
import kr.hhplus.be.server.domains.payment.application.usecase.ProcessPaymentService
import kr.hhplus.be.server.domains.payment.domain.repository.PaymentRepository
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentUseCaseConfig {

    @Bean
    fun findPendingPaymentInfoService(
        reservationRepository: ReservationRepository,
        seatRepository: SeatRepository,
    ): FindPendingPaymentInfoService {
        return FindPendingPaymentInfoService(
            reservationRepository,
            seatRepository
        )
    }

    @Bean
    fun processPaymentService(
        reservationRepository: ReservationRepository,
        seatRepository: SeatRepository,
        seatHoldRepository: SeatHoldRepository,
        pointRepository: PointRepository,
        pointHistoryRepository: PointHistoryRepository,
        paymentRepository: PaymentRepository,
        pointValidator: PointValidator,
        lockManager: LockManager
    ): ProcessPaymentService {
        return ProcessPaymentService(
            reservationRepository,
            seatRepository,
            seatHoldRepository,
            pointRepository,
            pointHistoryRepository,
            paymentRepository,
            pointValidator,
            lockManager
        )
    }

}