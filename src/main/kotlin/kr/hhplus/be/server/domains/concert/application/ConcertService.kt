package kr.hhplus.be.server.domains.concert.application

import kr.hhplus.be.server.common.exception.ConcertNotFoundException
import kr.hhplus.be.server.domains.concert.application.dto.response.ConcertDetailResponse
import kr.hhplus.be.server.domains.concert.application.dto.response.ConcertListResponse
import kr.hhplus.be.server.domains.concert.application.dto.response.ConcertSchedulesResponse
import kr.hhplus.be.server.domains.concert.domain.repository.ConcertRepository
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ConcertService(
    private val concertRepository: ConcertRepository,
    private val scheduleRepository: ScheduleRepository
) {
    fun findConcerts(): ConcertListResponse {
        return ConcertListResponse.from(concertRepository.findConcerts())
    }

    fun getConcertDetail(concertId: Long): ConcertDetailResponse {
        val concertScheduleQueryDto = concertRepository.findConcertDetailById(concertId)
            ?: throw ConcertNotFoundException()

        return ConcertDetailResponse.from(concertScheduleQueryDto)
    }

    fun findAvailableConcerts(concertId: Long): ConcertSchedulesResponse {
        val concert = concertRepository.findByIdOrNull(concertId)
            ?: throw ConcertNotFoundException()

        return ConcertSchedulesResponse.from(
            scheduleRepository.findAvailableConcerts(concert.id)
        )
    }
}