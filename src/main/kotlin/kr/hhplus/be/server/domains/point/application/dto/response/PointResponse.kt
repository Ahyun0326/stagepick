package kr.hhplus.be.server.domains.point.application.dto.response

import kr.hhplus.be.server.domains.point.domain.model.Point

data class PointResponse(
    val point: Int
) {
    companion object {
        fun from(point: Point): PointResponse {
            return PointResponse(point.point)
        }
    }
}