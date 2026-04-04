package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import com.querydsl.core.group.GroupBy.groupBy
import com.querydsl.core.group.GroupBy.list
import com.querydsl.core.group.GroupBy.sum
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import kr.hhplus.be.server.domains.concert.domain.model.QConcert.Companion.concert
import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto
import kr.hhplus.be.server.domains.schedule.domain.model.QSchedule.Companion.schedule
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.QSeatEntity.Companion.seatEntity
import org.springframework.stereotype.Repository

@Repository
class ReservationQueryRepositoryCustomImpl(
    private val queryFactory: JPAQueryFactory
) : ReservationQueryRepositoryCustom {

    override fun getWithDetailsById(reservationId: Long): ReservationPaymentDetailQueryDto? {
        val result: Map<Long, ReservationPaymentDetailQueryDto> = queryFactory
            .from(seatEntity)
            .innerJoin(seatEntity.schedule, schedule)
            .innerJoin(schedule.concert, concert)
            .where(seatEntity.reservationId.eq(reservationId))
            .transform(
                groupBy(seatEntity.reservationId).`as`(
                    Projections.constructor(
                        ReservationPaymentDetailQueryDto::class.java,
                        seatEntity.reservationId,
                        concert.title,
                        concert.location,
                        schedule.viewingTime,
                        schedule.concertedAt,
                        list(seatEntity.number),
                        sum(seatEntity.price)
                    )
                )
            )

        return result[reservationId]
    }
}