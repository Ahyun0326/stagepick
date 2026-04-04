package kr.hhplus.be.server.domains.point.infrastructure.persistence

import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class PointJpaRepository(
    private val springPointJpa: SpringPointJpa,
    private val pointMapper: PointMapper
) : PointRepository {
    override fun findPointByMemberId(memberId: Long): Optional<Point> {
        return springPointJpa.findByMemberId(memberId)
            .map { it.toDomain() }
    }

    override fun save(point: Point): Point {
        val entity = if (point.id != null) {
            springPointJpa.findById(point.id).orElseThrow()
        } else {
            PointEntity(memberId = point.memberId, point = point.point)
        }
        entity.updateFrom(point)
        val savedEntity = springPointJpa.save(entity)
        return savedEntity.toDomain()
    }
}
