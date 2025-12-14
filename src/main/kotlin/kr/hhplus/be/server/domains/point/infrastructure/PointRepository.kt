package kr.hhplus.be.server.domains.point.infrastructure

import kr.hhplus.be.server.domains.point.domain.Point
import org.springframework.data.jpa.repository.JpaRepository

interface PointRepository : JpaRepository<Point, Long> {
}