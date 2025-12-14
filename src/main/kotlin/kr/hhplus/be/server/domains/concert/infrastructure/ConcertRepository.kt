package kr.hhplus.be.server.domains.concert.infrastructure

import kr.hhplus.be.server.domains.concert.domain.Concert
import org.springframework.data.jpa.repository.JpaRepository

interface ConcertRepository : JpaRepository<Concert, Long> {
}