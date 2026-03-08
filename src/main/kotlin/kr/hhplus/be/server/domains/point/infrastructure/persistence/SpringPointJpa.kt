package kr.hhplus.be.server.domains.point.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SpringPointJpa : JpaRepository<PointEntity, Long> {
    fun findByMemberId(memberId: Long): Optional<PointEntity>
}
