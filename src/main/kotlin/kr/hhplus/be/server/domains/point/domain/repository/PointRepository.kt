package kr.hhplus.be.server.domains.point.domain.repository

import kr.hhplus.be.server.domains.point.domain.model.Point
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PointRepository : JpaRepository<Point, Long> {
    fun findPointByMemberId(memberId: Long): Optional<Point>
}