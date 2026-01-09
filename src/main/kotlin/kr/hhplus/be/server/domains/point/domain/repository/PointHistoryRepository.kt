package kr.hhplus.be.server.domains.point.domain.repository

import kr.hhplus.be.server.domains.point.domain.model.PointHistory
import org.springframework.data.jpa.repository.JpaRepository

interface PointHistoryRepository : JpaRepository<PointHistory, Long> {}