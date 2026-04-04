package kr.hhplus.be.server.domains.concert.infrastructure

import com.querydsl.core.types.dsl.DateTemplate
import com.querydsl.core.types.dsl.DateTimeExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertQueryDto
import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertScheduleQueryDto
import kr.hhplus.be.server.domains.concert.domain.model.QConcert.Companion.concert
import kr.hhplus.be.server.domains.concert.domain.repository.ConcertQueryRepositoryCustom
import kr.hhplus.be.server.domains.concert.domain.repository.projection.QConcertQueryDto
import kr.hhplus.be.server.domains.concert.domain.repository.projection.QConcertScheduleQueryDto
import kr.hhplus.be.server.domains.schedule.domain.model.QSchedule.Companion.schedule
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ConcertRepositoryImpl(
    private val queryFactory: JPAQueryFactory
): ConcertQueryRepositoryCustom {

    override fun findConcerts(): List<ConcertQueryDto> {
        return queryFactory
            .select(
                QConcertQueryDto(
                    concert.id,
                    concert.title,
                    concert.location,
                    schedule.concertedAt.min().toLocalDate(),
                    schedule.concertedAt.max().toLocalDate(),
                )
            )
            .from(concert)
            .leftJoin(schedule).on(concert.id.eq(schedule.concert.id))
            .groupBy(concert.id, concert.title, concert.location)
            .fetch()
    }

    override fun findConcertDetailById(concertId: Long): ConcertScheduleQueryDto? {
        return queryFactory.select(
            QConcertScheduleQueryDto(
                concert.id,
                concert.title,
                concert.location,
                schedule.viewingTime.max(),
                schedule.concertedAt.min().toLocalDate(),
                schedule.concertedAt.max().toLocalDate()
            )
        )
            .from(concert)
            .leftJoin(schedule).on(concert.id.eq(schedule.concert.id))
            .where(concert.id.eq(concertId))
            .groupBy(concert.id, concert.title, concert.location)
            .fetchOne()
    }

    fun <T : Comparable<*>> DateTimeExpression<T>.toLocalDate(): DateTemplate<LocalDate> {
        return Expressions.dateTemplate(
            LocalDate::class.java,
            "CAST({0} AS LocalDate)",
            this
        )
    }

}