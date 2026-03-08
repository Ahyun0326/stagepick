package kr.hhplus.be.server.domains.point.infrastructure.persistence

import kr.hhplus.be.server.domains.point.domain.model.PointHistory
import org.springframework.stereotype.Component

@Component
class PointHistoryMapper {
    fun toDomain(entity: PointHistoryEntity): PointHistory {
        return PointHistory(
            id = entity.id,
            memberId = entity.memberId,
            currentPoint = entity.currentPoint,
            changedPoint = entity.changedPoint,
            type = entity.type
        )
    }
}

fun PointHistoryEntity.toDomain(): PointHistory = PointHistory(
    id = this.id,
    memberId = this.memberId,
    currentPoint = this.currentPoint,
    changedPoint = this.changedPoint,
    type = this.type
)
