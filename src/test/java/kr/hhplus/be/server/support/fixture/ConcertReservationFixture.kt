package kr.hhplus.be.server.support.fixture

import kr.hhplus.be.server.domains.concert.domain.model.Concert
import kr.hhplus.be.server.domains.concert.domain.repository.ConcertRepository
import kr.hhplus.be.server.domains.member.domain.model.Member
import kr.hhplus.be.server.domains.member.domain.repository.MemberRepository
import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SeatEntity
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SpringSeatJpa
import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘서트 예약 시나리오용 통합 테스트 데이터 픽스처
 * - 회원 / 콘서트 / 회차 / 좌석을 실제 DB 에 저장한다
 * - 필수 통합 테스트 3종과 기능별 통합 테스트에서 재사용한다
 */
class ConcertReservationFixture(
    private val memberRepository: MemberRepository,
    private val concertRepository: ConcertRepository,
    private val scheduleRepository: ScheduleRepository,
    private val springSeatJpa: SpringSeatJpa
) {

    fun createMember(): Member {
        val loginId = "user-${UUID.randomUUID().toString().take(LOGIN_ID_SUFFIX_LENGTH)}"
        return memberRepository.save(Member.create(loginId, ENCODED_PASSWORD, USERNAME))
    }

    fun createScheduleWithSeats(seatCount: Int, price: Int): ScheduleWithSeats {
        val concert = concertRepository.save(Concert(CONCERT_TITLE, CONCERT_LOCATION))
        val schedule = scheduleRepository.save(
            Schedule(concert, LocalDateTime.now().plusDays(CONCERT_DAYS_LATER), VIEWING_TIME_MINUTES)
        )
        val seats = springSeatJpa.saveAll(
            (1..seatCount).map { SeatEntity(schedule = schedule, number = "A$it", price = price) }
        )

        return ScheduleWithSeats(scheduleId = schedule.id, seatIds = seats.map { it.id })
    }

    data class ScheduleWithSeats(
        val scheduleId: Long,
        val seatIds: List<Long>
    )

    companion object {
        private const val LOGIN_ID_SUFFIX_LENGTH = 8
        private const val ENCODED_PASSWORD = "encoded-password"
        private const val USERNAME = "테스트유저"
        private const val CONCERT_TITLE = "테스트 콘서트"
        private const val CONCERT_LOCATION = "테스트 공연장"
        private const val CONCERT_DAYS_LATER = 7L
        private const val VIEWING_TIME_MINUTES = 120
    }
}
