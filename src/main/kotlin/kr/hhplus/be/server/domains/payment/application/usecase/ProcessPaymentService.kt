package kr.hhplus.be.server.domains.payment.application.usecase

import kr.hhplus.be.server.common.exception.DuplicatePaymentException
import kr.hhplus.be.server.common.extension.throwIfNotNull
import kr.hhplus.be.server.common.exception.PaymentInfoNotFoundException
import kr.hhplus.be.server.common.exception.PointNotFoundException
import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.common.exception.ReservationSeatExpiredException
import kr.hhplus.be.server.domains.common.lock.LockManager
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.dto.PaymentResponse
import kr.hhplus.be.server.domains.payment.domain.model.Payment
import kr.hhplus.be.server.domains.payment.domain.model.PaymentLog
import kr.hhplus.be.server.domains.payment.domain.repository.PaymentRepository
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.model.PointHistory
import kr.hhplus.be.server.domains.point.domain.model.PointHistoryType
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class ProcessPaymentService(
    private val reservationRepository: ReservationRepository,
    private val seatRepository: SeatRepository,
    private val seatHoldRepository: SeatHoldRepository,
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val paymentRepository: PaymentRepository,
    private val pointValidator: PointValidator,
    private val lockManager: LockManager
) {
    fun process(request: PaymentRequest): PaymentResponse {

        val memberId = 1L

        // 동시성 방지를 위한 분산 락 적용
        return lockManager.runWithLock("reservation:${request.reservationId}:payment") {

            validatePaymentRequest(request)

            val reservation = getReservation(request.reservationId)
            val seats = getSeats(reservation)
            val point = getPoint(memberId)

            // 실제 결제 금액 조회
            validateSufficientBalanceForPayment(
                request.point,
                point.point,
                seats.sumOf { it.price }
            )

            confirmReservation(seats)
            processPointUsage(point, request.point, memberId)

            return@runWithLock PaymentResponse.from(
                processPayment(reservation, request.point)
            )
        }
    }

    private fun validatePaymentRequest(request: PaymentRequest) {
        // 중복 결제 검증
        paymentRepository.findByReservationId(request.reservationId)
            .throwIfNotNull(DuplicatePaymentException())

        // 포인트 음수 검증
        pointValidator.validateNegativePoint(request.point)
    }

    private fun getPoint(memberId: Long): Point {
        return pointRepository.findPointByMemberId(memberId)
            .orElseThrow { PointNotFoundException() }
    }

    private fun getSeats(reservation: Reservation): List<Seat> {
        // 배정된 좌석의 예약 유효 시간이 지났을 경우 결제 실패
        return seatRepository.findSeatsByReservationId(reservation.id)
            .ifEmpty { throw ReservationSeatExpiredException() }
    }

    private fun getReservation(reservationId: Long): Reservation {
        return reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException()
    }

    private fun validateSufficientBalanceForPayment(
        usagePoint: Int,
        memberBalance: Int,
        paymentAmount: Int
    ) {
        // 요청으로 들어온 결제 금액이 현재 결제 금액과 일치하지 않을 경우 결제 실패
        pointValidator.validatePaymentAmountMatch(paymentAmount, usagePoint)

        // 회원의 포인트가 결제 금액보다 부족할 경우 결제 실패
        pointValidator.validateInsufficientPoint(memberBalance, usagePoint)
    }

    private fun confirmReservation(seats: List<Seat>) {
        // 유효성 검증 성공 시 좌석 상태 변경 및 redis에서 제거
        seatRepository.updateStatusToReserved(seats.map { it.id })
        seatHoldRepository.removeSeats(seats.map { it.id })
    }

    private fun processPointUsage(
        point: Point,
        usagePoint: Int,
        memberId: Long
    ) {
        // 회원 포인트 감소
        val remainPoint = point.useAndGetRemained(usagePoint)

        pointRepository.save(point)
        pointHistoryRepository.save(PointHistory.create(memberId, remainPoint, usagePoint, PointHistoryType.USE))
    }

    private fun processPayment(reservation: Reservation, point: Int): Payment {
        // 결제 정보 생성
        val reservationPaymentDetailQueryDto =
            reservationRepository.getWithDetailsById(reservation.id) ?: throw PaymentInfoNotFoundException()

        val paymentLog = PaymentLog.create(reservationPaymentDetailQueryDto)

        return paymentRepository.save(
            Payment.create(reservation.id, point, paymentLog), reservation
        )
    }
}
