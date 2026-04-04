package kr.hhplus.be.server.domains.concert.domain.repository

import kr.hhplus.be.server.domains.concert.domain.model.Concert
import org.springframework.data.jpa.repository.JpaRepository

interface ConcertRepository : JpaRepository<Concert, Long>, ConcertQueryRepositoryCustom {
}