package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto

interface ReservationQueryRepositoryCustom {
    fun getWithDetailsById(reservationId: Long): ReservationPaymentDetailQueryDto?
}