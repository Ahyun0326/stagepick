package kr.hhplus.be.server.domains.payment.application.usecase

import kr.hhplus.be.server.common.exception.PaymentInfoNotFoundException
import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.common.exception.ReservationSeatNotFoundException
import kr.hhplus.be.server.domains.payment.application.dto.PendingPaymentInfoResponse
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class FindPendingPaymentInfoService(
    private val reservationRepository: ReservationRepository,
    private val seatRepository: SeatRepository,
) {
    fun invoke(reservationId: Long): PendingPaymentInfoResponse {
        // 예약 정보 조회
        val reservation = reservationRepository.findById(reservationId)
            ?: throw ReservationNotFoundException()

        // 예약 정보에 해당하는 좌석이 없는 경우
        val seats = seatRepository.findSeatsByReservationId(reservation.id)
        check(seats.isNotEmpty()) { throw ReservationSeatNotFoundException() }

        val reservationPaymentDetailQueryDto =
            reservationRepository.getWithDetailsById(reservationId) ?: throw PaymentInfoNotFoundException()

        return PendingPaymentInfoResponse.from(reservationPaymentDetailQueryDto)
    }

}