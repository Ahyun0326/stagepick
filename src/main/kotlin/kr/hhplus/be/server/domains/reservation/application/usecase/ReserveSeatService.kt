package kr.hhplus.be.server.domains.reservation.application.usecase

import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.common.lock.distributedLock
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationResponse
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.application.validator.SeatValidator
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

/**
 * 순수 유스케이스 클래스
 * - @Service, 스프링 의존성 제거
 * -
 */
class ReserveSeatService(
    private val scheduleRepository: ScheduleRepository,
    private val seatRepository: SeatRepository,
    private val reservationRepository: ReservationRepository,
    private val seatHoldRepository: SeatHoldRepository,
    private val seatValidator: SeatValidator,
) {
    fun invoke(
        reservationRequest: ReservationRequest
    ): ReservationResponse {
        // 공연 회차 정보 조회
        if (!scheduleRepository.existsById(reservationRequest.scheduleId)) {
            throw ScheduleNotFoundException()
        }

        // 분산 락 적용
        return distributedLock("schedule:${reservationRequest.scheduleId}:seat-reservation") {
            // 존재하는 좌석인지 확인
            val seats = seatRepository.findSeats(reservationRequest.scheduleId, reservationRequest.seatIds)

            seatValidator.validateExistingSeats(
                existingIds = seats.map { it.id },
                requestSeatIds = reservationRequest.seatIds
            )

            // 예약 가능한 좌석인지 확인
            seatValidator.validateAvailableSeats(
                seats.filter { !it.validateAvailable() }.map { it.id }
            )

            // 이용 가능하면 좌석 예약 진행
            val reservation = reservationRepository.save(Reservation.create())

            seats.forEach { it.reserve(reservation) }
            seatRepository.saveAll(seats)

            // 좌석 배정 시간 5분으로 설정
            seatHoldRepository.hold(seats)

            return@distributedLock ReservationResponse(reservation.id)
        }
    }
}