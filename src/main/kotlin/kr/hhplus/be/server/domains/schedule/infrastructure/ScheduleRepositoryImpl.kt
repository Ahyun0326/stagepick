package kr.hhplus.be.server.domains.schedule.infrastructure

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import kr.hhplus.be.server.domains.schedule.domain.model.QSchedule.Companion.schedule
import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleQueryRepositoryCustom
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ScheduleRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : ScheduleQueryRepositoryCustom {

    override fun findAvailableConcerts(concertId: Long): List<Schedule> {
        val whereClause = BooleanBuilder()

        whereClause.and(schedule.concert.id.eq(concertId))
        whereClause.and(schedule.concertedAt.goe(LocalDateTime.now()))

        return queryFactory.selectFrom(schedule)
            .where(whereClause)
            .fetch()
    }
}