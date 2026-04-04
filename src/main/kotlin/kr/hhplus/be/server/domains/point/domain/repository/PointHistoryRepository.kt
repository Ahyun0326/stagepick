package kr.hhplus.be.server.domains.point.domain.repository

import kr.hhplus.be.server.domains.point.domain.model.PointHistory

interface PointHistoryRepository {
    fun save(pointHistory: PointHistory): PointHistory
}