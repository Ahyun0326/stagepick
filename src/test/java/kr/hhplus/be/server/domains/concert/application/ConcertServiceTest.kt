package kr.hhplus.be.server.domains.concert.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.common.exception.ConcertNotFoundException
import kr.hhplus.be.server.domains.concert.domain.model.Concert
import kr.hhplus.be.server.domains.concert.domain.repository.ConcertRepository
import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertQueryDto
import kr.hhplus.be.server.domains.concert.domain.repository.projection.ConcertScheduleQueryDto
import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDateTime

class ConcertServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val concertRepository: ConcertRepository = mockk()
    val scheduleRepository: ScheduleRepository = mockk()

    val concertService = ConcertService(concertRepository, scheduleRepository)


    given("등록된 콘서트가 없을 때") {
        every { concertRepository.findConcerts() } returns emptyList()

        `when`("전체 콘서트 목록을 조회하면") {
            val results = concertService.findConcerts()

            then("빈 리스트가 반환된다") {
                results.shouldNotBeNull {
                    this.concerts shouldHaveSize 0
                }
            }
        }
    }

    given("콘서트가 5개 등록되어 있을 때") {
        val concertQueryDtos = (1..5).map { i ->
            ConcertQueryDto(
                concertId = i.toLong(),
                title = "concert$i",
                location = "location$i",
                startedAt = mockk(),
                expiredAt = mockk()
            )
        }

        every { concertRepository.findConcerts() } returns concertQueryDtos

        `when`("전체 콘서트 목록을 조회하면") {
            val results = concertService.findConcerts()

            then("5개의 콘서트 정보가 반환되어야 한다") {
                results.shouldNotBeNull {
                    // 개수 검증
                    this.concerts shouldHaveSize 5

                    // 예상 리스트와 실제 리스트를 묶어서 비교
                    this.concerts.zip(concertQueryDtos).forAll { (actual, expected) ->
                        actual.concertId shouldBe expected.concertId
                        actual.title shouldBe expected.title
                        actual.location shouldBe expected.location
                    }

                }
            }
        }
    }

    given("concertId가 1인 콘서트가 있을 때") {
        val concertId = 1L

        val concertScheduleQueryDto = ConcertScheduleQueryDto(
            concertId = concertId,
            title = "concert1",
            location = "location1",
            viewingTime = 2,
            startedAt = mockk(),
            expiredAt = mockk(),
        )

        every { concertRepository.findConcertDetailById(concertId) } returns concertScheduleQueryDto

        `when`("해당 콘서트의 상세 정보를 조회하면") {
            val result = concertService.getConcertDetail(concertId)

            then("요청한 concertId와 일치하는 콘서트 정보가 반환된다") {
                result.shouldNotBeNull {
                    this.concertId shouldBe concertId
                    this.title shouldBe concertScheduleQueryDto.title
                    this.location shouldBe concertScheduleQueryDto.location
                    this.viewingTime shouldBe concertScheduleQueryDto.viewingTime
                }
            }

        }
    }

    given("존재하지 않는 콘서트의 상세 정보를 조회하려고 할 때") {
        val concertId = 1L

        every { concertRepository.findConcertDetailById(concertId) } returns null

        `when`("조회를 요청하면") {
            then("ConcertNotFoundException이 발생한다") {
                shouldThrow<ConcertNotFoundException> {
                    concertService.getConcertDetail(concertId)
                }
            }
        }
    }

    given("예약 가능한 콘서트가 있을 때") {

        val concertId = 1L
        val concert = mockk<Concert>()
        every { concert.id } returns concertId

        val now = LocalDateTime.now()

        val schedules = (1..2).map { i ->
            val schedule = mockk<Schedule>()
            every { schedule.id } returns i.toLong()
            every { schedule.concert } returns concert
            every { schedule.concertedAt } returns now.plusDays(i.toLong())
            every { schedule.viewingTime } returns 2
            schedule
        }

        every { concertRepository.findByIdOrNull(concertId) } returns concert
        every { scheduleRepository.findAvailableConcerts(concertId) } returns schedules

        `when`("해당 concertId로 예약 가능한 날짜를 조회하면") {
            val results = concertService.findAvailableConcerts(concertId)

            then("예약 가능한 날짜 목록이 반환된다") {
                results.shouldNotBeNull {
                    // 개수 검증
                    this.reservationDates shouldHaveSize 2

                    // 예상 리스트와 실제 리스트를 묶어서 비교
                    this.reservationDates.zip(schedules).forAll { (actual, expected) ->
                        actual.scheduleId shouldBe expected.id
                        actual.datetime shouldBe expected.concertedAt
                    }
                }
            }
        }
    }

    given("존재하지 않는 콘서트의 예약 가능 날짜를 조회하려고 할 때") {
        val concertId = 1L

        every { concertRepository.findByIdOrNull(concertId) } returns null

        `when`("조회를 요청하면") {
            then("ConcertNotFoundException이 발생한다") {
                shouldThrow<ConcertNotFoundException> {
                    concertService.findAvailableConcerts(concertId)
                }
            }
        }
    }

    given("존재하는 콘서트의 예약 가능 날짜가 없을 때") {
        val concertId = 1L
        val concert = mockk<Concert>()

        every { concert.id } returns concertId
        every { concertRepository.findByIdOrNull(concertId) } returns concert
        every { scheduleRepository.findAvailableConcerts(concertId) } returns emptyList()

        `when`("조회를 요청하면") {
            val results = concertService.findAvailableConcerts(concertId)

            then("빈 리스트가 반환된다") {
                results.shouldNotBeNull{
                    this.reservationDates shouldHaveSize 0
                }
            }
        }
    }

})
