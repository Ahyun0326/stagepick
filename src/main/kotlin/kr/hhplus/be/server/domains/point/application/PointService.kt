package kr.hhplus.be.server.domains.point.application

import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.dto.response.PointResponse
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.model.PointHistory
import kr.hhplus.be.server.domains.point.domain.model.PointHistoryType
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PointService(
    private val pointRepository: PointRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val pointValidator: PointValidator
) {

    @Transactional
    fun chargePoint(request: ChargePointRequest) {
        val memberId = 1L

        pointValidator.validateNegativePoint(request.amount)
        val findPoint = pointRepository.findPointByMemberId(memberId)

        findPoint.ifPresent {
            val currentPoint = it.chargePoint(request.amount)
            savePointHistory(memberId, currentPoint, request.amount)
        }

        if (findPoint.isEmpty) {
            pointRepository.save(Point(memberId, request.amount))
            savePointHistory(memberId, request.amount, request.amount)
        }
    }

    fun savePointHistory(memberId: Long, currentPoint: Int, chargedPoint: Int) {
        pointHistoryRepository.save(
            PointHistory(
                memberId,
                currentPoint,
                chargedPoint,
                PointHistoryType.CHARGE.name
            )
        )
    }

    fun findPoint(): PointResponse {
        val memberId = 1L
        val point = pointRepository.findPointByMemberId(memberId)
            .orElseGet { Point(memberId, 0) }

        return PointResponse.from(point)
    }
}