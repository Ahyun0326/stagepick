package kr.hhplus.be.server.domains.schedule.infrastructure

import kr.hhplus.be.server.domains.schedule.domain.Schedule
import org.springframework.data.jpa.repository.JpaRepository

interface ScheduleRepository : JpaRepository<Schedule, Long> {
}