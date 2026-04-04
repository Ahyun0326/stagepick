package kr.hhplus.be.server.domains.point.domain.repository

import kr.hhplus.be.server.domains.point.domain.model.Point
import java.util.Optional

interface PointRepository {
    fun findPointByMemberId(memberId: Long): Optional<Point>
    fun save(point: Point): Point
}