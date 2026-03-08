package kr.hhplus.be.server.domains.point.infrastructure.persistence

import kr.hhplus.be.server.domains.point.domain.model.Point
import org.springframework.stereotype.Component

@Component
class PointMapper {
    fun toDomain(entity: PointEntity): Point {
        return Point(
            id = entity.id,
            memberId = entity.memberId,
            point = entity.point
        )
    }
}

fun PointEntity.toDomain(): Point = Point(
    id = this.id,
    memberId = this.memberId,
    point = this.point
)
