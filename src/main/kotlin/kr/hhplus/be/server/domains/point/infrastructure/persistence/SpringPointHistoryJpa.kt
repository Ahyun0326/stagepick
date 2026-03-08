package kr.hhplus.be.server.domains.point.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SpringPointHistoryJpa : JpaRepository<PointHistoryEntity, Long>
