package kr.hhplus.be.server.domains.concert.application.dto.response

import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import java.time.LocalDateTime

class ConcertScheduleResponse(
    val scheduleId: Long,
    val datetime: LocalDateTime
) {
    companion object {
        fun from(schedule: Schedule): ConcertScheduleResponse {
            return ConcertScheduleResponse(
                schedule.id,
                schedule.concertedAt
            )
        }
    }

}