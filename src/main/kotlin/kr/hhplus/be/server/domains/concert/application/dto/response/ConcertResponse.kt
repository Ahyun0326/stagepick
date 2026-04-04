package kr.hhplus.be.server.domains.concert.application.dto.response

import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertQueryDto
import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import java.time.LocalDate
import java.time.LocalDateTime

data class ConcertResponse(
    val concertId: Long,
    val title: String,
    val location: String,
    val startedAt: LocalDate,
    val expiredAt: LocalDate
) {
    companion object {
        fun from(concertQueryDto: ConcertQueryDto): ConcertResponse {
            return ConcertResponse(
                concertQueryDto.concertId,
                concertQueryDto.title,
                concertQueryDto.location,
                concertQueryDto.startedAt,
                concertQueryDto.expiredAt,
            )
        }
    }
}