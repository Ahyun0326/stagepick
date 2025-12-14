package kr.hhplus.be.server.domains.point.infrastructure

import kr.hhplus.be.server.domains.point.domain.PointHistory
import org.springframework.data.jpa.repository.JpaRepository

interface PointHistoryRepository : JpaRepository<PointHistory, Long> {
}