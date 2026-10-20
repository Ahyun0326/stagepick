package kr.hhplus.be.server

import kr.hhplus.be.server.domains.concert.domain.repository.ConcertRepository
import kr.hhplus.be.server.domains.member.domain.repository.MemberRepository
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SpringSeatJpa
import kr.hhplus.be.server.support.DatabaseCleaner
import kr.hhplus.be.server.support.RedisCleaner
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import javax.sql.DataSource

@TestConfiguration
class IntegrationTestConfiguration {

    @Bean
    fun databaseCleaner(dataSource: DataSource) = DatabaseCleaner(dataSource)

    @Bean
    fun redisCleaner(redisConnectionFactory: RedisConnectionFactory) = RedisCleaner(redisConnectionFactory)

    @Bean
    fun concertReservationFixture(
        memberRepository: MemberRepository,
        concertRepository: ConcertRepository,
        scheduleRepository: ScheduleRepository,
        springSeatJpa: SpringSeatJpa
    ) = ConcertReservationFixture(memberRepository, concertRepository, scheduleRepository, springSeatJpa)
}