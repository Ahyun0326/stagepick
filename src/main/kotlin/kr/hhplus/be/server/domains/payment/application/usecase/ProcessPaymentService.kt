package kr.hhplus.be.server.domains.payment.application.usecase

import kr.hhplus.be.server.common.exception.DuplicatePaymentException
import kr.hhplus.be.server.common.extension.throwIfNotNull
import kr.hhplus.be.server.common.exception.InsufficientPointException
import kr.hhplus.be.server.common.exception.PaymentInfoNotFoundException
import kr.hhplus.be.server.common.exception.PointNotFoundException
import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.common.exception.ReservationSeatExpiredException
import kr.hhplus.be.server.domains.common.lock.LockManager
import kr.hhplus.be.server.domains.common.transaction.AfterCommitExecutor
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
    private val lockManager: LockManager,
    private val afterCommitExecutor: AfterCommitExecutor
) {
    fun process(memberId: Long, request: PaymentRequest): PaymentResponse {
        // 동시성 방지를 위한 분산 락 적용
        return lockManager.runWithLock("reservation:${request.reservationId}:payment") {

            validatePaymentRequest(request)

            val reservation = getReservation(memberId, request.reservationId)
            val seats = getSeats(reservation)
            val point = getPoint(memberId)

            // 실제 결제 금액 조회
            validateSufficientBalanceForPayment(
                request.point,
                point.point,
                seats.sumOf { it.price }
            )

            confirmReservation(reservation, seats)
            processPointUsage(memberId, request.point)

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
        // - 스케줄러가 이미 해제한 경우: 예약에 연결된 좌석이 없음
        // - 만료 시각은 지났지만 스케줄러가 아직 해제하지 않은 경우: 홀드 만료 시각으로 판단
        val seats = seatRepository.findSeatsByReservationId(reservation.id)
            .ifEmpty { throw ReservationSeatExpiredException() }

        if (!seatHoldRepository.isAllHoldActive(seats.map { it.id })) {
            throw ReservationSeatExpiredException()
        }

        return seats
    }

    private fun getReservation(memberId: Long, reservationId: Long): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException()
        if (reservation.memberId != memberId) throw ReservationNotFoundException()

        return reservation
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

    private fun confirmReservation(reservation: Reservation, seats: List<Seat>) {
        val seatIds = seats.map { it.id }

        // 해당 예약이 HOLD 중인 좌석만 확정 (조건부 UPDATE). 홀드 확인 이후 만료 처리가 먼저 해제했다면
        // 일부/전부 0건이 되므로 예외 → 롤백. 이 단계가 첫 쓰기라 되돌릴 다른 변경은 없다
        val confirmed = seatRepository.confirmHeldSeats(seatIds, reservation.id)
        if (confirmed != seatIds.size) {
            throw ReservationSeatExpiredException()
        }

        // Redis 홀드 제거는 DB 커밋 이후에만 실행 (이후 단계에서 롤백되면 홀드 유지 → 만료 시 정상 해제)
        afterCommitExecutor.execute { seatHoldRepository.removeSeats(seatIds) }
    }

    private fun processPointUsage(memberId: Long, usagePoint: Int) {
        // 조건부 UPDATE(point = point - amount, point >= amount)로 원자적 차감.
        // 위에서 이미 잔액을 확인했더라도, 같은 회원의 다른 예약 결제가 동시에 끼어들면
        // 이 조건부 UPDATE가 0건을 반환해 최종 방어선 역할을 한다 (lost update 방지).
        val usedRows = pointRepository.usePoint(memberId, usagePoint)
        if (usedRows == 0) throw InsufficientPointException()

        val remainPoint = pointRepository.findPointByMemberId(memberId)
            .orElseThrow { PointNotFoundException() }
            .point

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
