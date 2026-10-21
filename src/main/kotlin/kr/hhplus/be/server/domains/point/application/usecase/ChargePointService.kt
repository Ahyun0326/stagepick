package kr.hhplus.be.server.domains.point.application.usecase

import kr.hhplus.be.server.common.exception.PointNotFoundException
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
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

        // 조건부 UPDATE(point = point + amount)로 원자적 충전. 포인트 행이 없는 최초 충전이거나,
        // 최초 충전이 동시에 경합하는 경우까지 저장소(chargePoint)가 책임진다 — lost update·중복 행 방지.
        pointRepository.chargePoint(memberId, request.amount)

        val currentPoint = pointRepository.findPointByMemberId(memberId)
            .orElseThrow { PointNotFoundException() }
            .point

        savePointHistory(memberId, currentPoint, request.amount)
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
