package kr.hhplus.be.server.domains.concert.domain.repository

import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertQueryDto
import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertScheduleQueryDto

interface ConcertQueryRepositoryCustom {
    fun findConcerts(): List<ConcertQueryDto>
    fun findConcertDetailById(concertId: Long): ConcertScheduleQueryDto?
}