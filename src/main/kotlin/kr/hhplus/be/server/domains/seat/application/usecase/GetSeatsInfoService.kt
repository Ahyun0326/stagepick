package kr.hhplus.be.server.domains.seat.application.usecase

import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.application.dto.SeatsInfo
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class GetSeatsInfoService(
    private val seatRepository: SeatRepository,
    private val scheduleRepository: ScheduleRepository
) {

    /**
     * 좌석 정보 조회 유스케이스
     */
    fun invoke(scheduleId: Long): SeatsInfo {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw ScheduleNotFoundException()
        }

        return SeatsInfo.from(
            seatRepository.findSeatsByScheduleId(scheduleId)
        )
    }
}