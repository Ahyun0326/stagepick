package kr.hhplus.be.server.domains.reservation.domain.model

import io.hypersistence.tsid.TSID

class Reservation(
//    private var member: Member = member
            val number: String
) {
    var id: Long = 0L

    companion object {
        fun create(): Reservation {
            return Reservation(
                number = TSID.fast().toLowerCase()
            )
        }
    }

    fun assignId(id: Long) {
        this.id = id
    }
}