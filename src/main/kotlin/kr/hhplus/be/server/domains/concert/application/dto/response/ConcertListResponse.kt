package kr.hhplus.be.server.domains.concert.application.dto.response

import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertQueryDto

class ConcertListResponse(
    val concerts: List<ConcertResponse>
) {

    companion object {
        fun from(concertQueryDtos: List<ConcertQueryDto>): ConcertListResponse {
            return ConcertListResponse(
                concertQueryDtos.map {
                    ConcertResponse.from(it)
                }
            )
        }
    }
}