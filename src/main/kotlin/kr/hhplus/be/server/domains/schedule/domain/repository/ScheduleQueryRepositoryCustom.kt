package kr.hhplus.be.server.domains.schedule.domain.repository

import kr.hhplus.be.server.domains.schedule.domain.model.Schedule

interface ScheduleQueryRepositoryCustom {
    fun findAvailableConcerts(concertId: Long): List<Schedule>
}