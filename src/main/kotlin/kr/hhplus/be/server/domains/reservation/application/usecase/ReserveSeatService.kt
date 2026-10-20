package kr.hhplus.be.server.domains.reservation.application.usecase

import kr.hhplus.be.server.common.exception.SeatUnavailableException
import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.domains.common.lock.LockManager
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
    private val lockManager: LockManager
) {
    fun invoke(
        memberId: Long,
        reservationRequest: ReservationRequest
    ): ReservationResponse {
        // 공연 회차 정보 조회
        if (!scheduleRepository.existsById(reservationRequest.scheduleId)) {
            throw ScheduleNotFoundException()
        }

        // 분산 락 적용
        return lockManager.runWithLock("schedule:${reservationRequest.scheduleId}:seat-reservation") {
            // 존재하는 좌석인지 확인
            val seats = seatRepository.findSeats(reservationRequest.scheduleId, reservationRequest.seatIds)

            seatValidator.validateExistingSeats(
                existingIds = seats.map { it.id },
                requestSeatIds = reservationRequest.seatIds
            )

            // 예약 가능한 좌석인지 확인
            seatValidator.validateAvailableSeats(
                seats.filter { !it.isAvailable() }.map { it.id }
            )

            // 이용 가능하면 좌석 예약 진행
            val reservation = reservationRepository.save(Reservation.create(memberId))

            // AVAILABLE 일 때만 HOLD 로 바꾸는 조건부 UPDATE (분산락 다음의 두 번째 방어선)
            // 일부라도 선점됐으면 예외 → 같은 트랜잭션의 예약 저장까지 롤백
            val held = seatRepository.holdAvailableSeats(reservationRequest.seatIds, reservation.id)
            if (held != reservationRequest.seatIds.size) {
                throw SeatUnavailableException(reservationRequest.seatIds)
            }

            // 좌석 배정 시간 5분으로 설정
            seatHoldRepository.hold(seats)

            return@runWithLock ReservationResponse(reservation.id)
        }
    }
}
