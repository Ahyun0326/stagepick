package kr.hhplus.be.server.domains.concert.presentation.web

import kr.hhplus.be.server.common.response.ApiResponse
import kr.hhplus.be.server.domains.concert.application.ConcertService
import kr.hhplus.be.server.domains.concert.application.dto.response.ConcertDetailResponse
import kr.hhplus.be.server.domains.concert.application.dto.response.ConcertListResponse
import kr.hhplus.be.server.domains.concert.application.dto.response.ConcertSchedulesResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/concerts")
class ConcertController(
    private val concertService: ConcertService,
) {
    @GetMapping
    fun findConcerts(): ApiResponse<ConcertListResponse> {
        return ApiResponse.Companion.success(concertService.findConcerts())
    }

    @GetMapping("/{concertId}")
    fun getConcertDetail(@PathVariable concertId: Long): ApiResponse<ConcertDetailResponse> {
        return ApiResponse.Companion.success(concertService.getConcertDetail(concertId))
    }

    @GetMapping("/{concertId}/schedules")
    fun findAvailableConcerts(@PathVariable concertId: Long): ApiResponse<ConcertSchedulesResponse> {
        return ApiResponse.Companion.success(concertService.findAvailableConcerts(concertId))
    }
}