package kr.hhplus.be.server.domains.seat.application.usecase

import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.application.dto.SeatsInfo
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class GetSeatsInfoService(
    private val seatRepository: SeatRepository,
    private val scheduleRepository: ScheduleRepository,
    private val seatHoldRepository: SeatHoldRepository
) {

    /**
     * 좌석 정보 조회 유스케이스
     */
    fun invoke(scheduleId: Long): SeatsInfo {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw ScheduleNotFoundException()
        }

        // 전체 좌석 정보 조회
        val seats = seatRepository.findSeatsByScheduleId(scheduleId)
        val seatIds = seats.map { it.id }

        // redis에서 임시 배정된 좌석 정보 조회
        val heldStatuses = seatHoldRepository.getHeldStatus(seatIds)

        // DB 정보 + Redis 정보
        return SeatsInfo.of(seats, heldStatuses)
    }
}