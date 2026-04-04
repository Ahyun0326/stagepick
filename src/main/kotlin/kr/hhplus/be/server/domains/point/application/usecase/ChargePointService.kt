package kr.hhplus.be.server.domains.point.application.usecase

import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.model.PointHistory
import kr.hhplus.be.server.domains.point.domain.model.PointHistoryType
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository

/**
 * 순수 유스케이스 클래스 - 포인트 충전
 * - @Service, 스프링 의존성 제거
 * - 비즈니스 로직만 포함
 */
class ChargePointService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointValidator: PointValidator
) {
    fun invoke(memberId: Long, request: ChargePointRequest) {
        pointValidator.validateNegativePoint(request.amount)
        val findPoint = pointRepository.findPointByMemberId(memberId)

        findPoint.ifPresent {
            val currentPoint = it.chargeAndGetRemained(request.amount)
            pointRepository.save(it)
            savePointHistory(memberId, currentPoint, request.amount)
        }

        if (findPoint.isEmpty) {
            val newPoint = Point(memberId = memberId, point = request.amount)
            pointRepository.save(newPoint)
            savePointHistory(memberId, request.amount, request.amount)
        }
    }

    private fun savePointHistory(memberId: Long, currentPoint: Int, chargedPoint: Int) {
        pointHistoryRepository.save(
            PointHistory(
                memberId = memberId,
                currentPoint = currentPoint,
                changedPoint = chargedPoint,
                type = PointHistoryType.CHARGE.name
            )
        )
    }
}
