package kr.hhplus.be.server.domains.point.domain.model

class PointHistory(
    val id: Long? = null,
    val memberId: Long,
    val currentPoint: Int,
    val changedPoint: Int,
    val type: String
) {
    companion object {
        fun create(memberId: Long, remainPoint: Int, point: Int, type: PointHistoryType): PointHistory {
            return PointHistory(
                memberId = memberId,
                currentPoint = remainPoint,
                changedPoint = point,
                type = type.name
            )
        }
    }
}