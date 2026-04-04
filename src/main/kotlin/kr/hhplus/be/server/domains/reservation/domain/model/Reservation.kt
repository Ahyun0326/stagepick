package kr.hhplus.be.server.domains.reservation.domain.model

import io.hypersistence.tsid.TSID

class Reservation(
    val number: String,
    val memberId: Long
) {
    var id: Long = 0L

    companion object {
        fun create(): Reservation {
            return Reservation(
                memberId = 1L,
                number = TSID.fast().toLowerCase()
            )
        }
    }

    fun assignId(id: Long) {
        this.id = id
    }
}