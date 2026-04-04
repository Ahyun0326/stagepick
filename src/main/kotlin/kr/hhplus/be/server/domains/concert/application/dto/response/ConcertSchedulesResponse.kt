package kr.hhplus.be.server.domains.concert.application.dto.response

import kr.hhplus.be.server.domains.schedule.domain.model.Schedule

class ConcertSchedulesResponse(
    val reservationDates: List<ConcertScheduleResponse>
) {
    companion object {
        fun from(schedules: List<Schedule>): ConcertSchedulesResponse {
            return ConcertSchedulesResponse(
                schedules.map {
                    ConcertScheduleResponse.from(it)
                }
            )
        }
    }

}