package kr.hhplus.be.server.domains.point.domain.model

class Point(
    val id: Long? = null,
    val memberId: Long,
    var point: Int
) {
    fun chargeAndGetRemained(amount: Int): Int {
        point += amount
        return point
    }

    fun useAndGetRemained(amount: Int): Int {
        point -= amount
        return point
    }
}