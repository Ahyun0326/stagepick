package kr.hhplus.be.server.domains.point.application.usecase

import kr.hhplus.be.server.domains.point.application.dto.response.PointResponse
import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository

/**
 * 순수 유스케이스 클래스 - 포인트 조회
 * - @Service, 스프링 의존성 제거
 * - 비즈니스 로직만 포함
 */
class FindPointService(
    private val pointRepository: PointRepository
) {
    fun invoke(memberId: Long): PointResponse {
        val point = pointRepository.findPointByMemberId(memberId)
            .orElseGet { Point(memberId = memberId, point = 0) }

        return PointResponse.from(point)
    }
}
