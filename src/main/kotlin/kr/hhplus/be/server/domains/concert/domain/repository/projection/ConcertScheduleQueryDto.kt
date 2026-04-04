package kr.hhplus.be.server.domains.concert.domain.repository.projection

import com.querydsl.core.annotations.QueryProjection
import java.time.LocalDate

@QueryProjection
data class ConcertScheduleQueryDto(
    val concertId: Long,
    val title: String,
    val location: String,
    val viewingTime: Int,
    val startedAt: LocalDate,
    val expiredAt: LocalDate
) {}