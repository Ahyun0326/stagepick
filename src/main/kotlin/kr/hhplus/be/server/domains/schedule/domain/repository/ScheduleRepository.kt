package kr.hhplus.be.server.domains.schedule.domain.repository

import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import org.springframework.data.jpa.repository.JpaRepository

interface ScheduleRepository : JpaRepository<Schedule, Long>, ScheduleQueryRepositoryCustom {}