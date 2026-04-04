package kr.hhplus.be.server.domains.concert.application.dto.response

import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertScheduleQueryDto
import java.time.LocalDate

data class ConcertDetailResponse(
    val concertId: Long,
    val title: String,
    val location: String,
    val viewingTime: Int,
    val startedAt: LocalDate,
    val expiredAt: LocalDate,
    val price: Int
) {

    companion object {
        fun from(concertScheduleQueryDto: ConcertScheduleQueryDto): ConcertDetailResponse {
            return ConcertDetailResponse(
                concertScheduleQueryDto.concertId,
                concertScheduleQueryDto.title,
                concertScheduleQueryDto.location,
                concertScheduleQueryDto.viewingTime,
                concertScheduleQueryDto.startedAt,
                concertScheduleQueryDto.expiredAt,
                price = 100000
            )
        }
    }
}