package kr.hhplus.be.server.domains.point.infrastructure.persistence

import kr.hhplus.be.server.domains.point.domain.model.PointHistory
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import org.springframework.stereotype.Repository

@Repository
class PointHistoryJpaRepository(
    private val springPointHistoryJpa: SpringPointHistoryJpa
) : PointHistoryRepository {
    override fun save(pointHistory: PointHistory): PointHistory {
        val entity = PointHistoryEntity(
            memberId = pointHistory.memberId,
            currentPoint = pointHistory.currentPoint,
            changedPoint = pointHistory.changedPoint,
            type = pointHistory.type
        )
        val savedEntity = springPointHistoryJpa.save(entity)
        return savedEntity.toDomain()
    }
}
