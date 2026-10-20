package kr.hhplus.be.server.domains.seat.infrastructure.persistence

import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate

/**
 * 좌석 상태 전이는 모두 "현재 상태"를 WHERE 에 거는 조건부 UPDATE 로 수행한다.
 * 락이 없거나 체크와 쓰기 사이에 다른 트랜잭션이 끼어들어도, DB 가 마지막으로 잘못된 전이를 막는다.
 */
@DisplayName("[통합] 좌석 저장소 조건부 상태 전이")
class SeatRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture
    @Autowired private lateinit var seatRepository: SeatRepository
    @Autowired private lateinit var reservationRepository: ReservationRepository
    @Autowired private lateinit var springSeatJpa: SpringSeatJpa
    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `결제 확정은 해당 예약이 HOLD 중인 좌석만 RESERVED 로 바꾼다`() {
        // given
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, PRICE)
        val reservationId = holdSeats(scheduleId, seatIds)

        // when
        val confirmed = inTx { seatRepository.confirmHeldSeats(seatIds, reservationId) }

        // then
        confirmed shouldBe SEAT_COUNT
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.RESERVED.name
            it.reservationId shouldBe reservationId
        }
    }

    @Test
    fun `만료 처리로 이미 풀린 좌석은 결제 확정되지 않는다`() {
        // given - 홀드 후, 결제가 확정하기 직전에 만료 처리가 먼저 해제를 커밋한 상황
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, PRICE)
        val reservationId = holdSeats(scheduleId, seatIds)
        inTx { seatRepository.releaseHeldSeats(seatIds) }

        // when
        val confirmed = inTx { seatRepository.confirmHeldSeats(seatIds, reservationId) }

        // then - 0건 확정, 좌석은 예약에 연결되지 않은 AVAILABLE 그대로 (RESERVED + reservationId=null 고아 좌석 없음)
        confirmed shouldBe 0
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.AVAILABLE.name
            it.reservationId.shouldBeNull()
        }
    }

    @Test
    fun `다른 예약이 잡은 좌석은 결제 확정되지 않는다`() {
        // given
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, PRICE)
        val ownerReservationId = holdSeats(scheduleId, seatIds)
        val otherReservationId = reservationRepository.save(Reservation.create(OTHER_MEMBER_ID)).id

        // when
        val confirmed = inTx { seatRepository.confirmHeldSeats(seatIds, otherReservationId) }

        // then
        confirmed shouldBe 0
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe ownerReservationId
        }
    }

    @Test
    fun `만료 해제는 HOLD 인 좌석만 풀고 결제된 좌석은 그대로 둔다`() {
        // given - 좌석 2개 중 1개만 결제 확정
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, PRICE)
        val reservationId = holdSeats(scheduleId, seatIds)
        inTx { seatRepository.confirmHeldSeats(listOf(seatIds.first()), reservationId) }

        // when
        val released = inTx { seatRepository.releaseHeldSeats(seatIds) }

        // then
        released shouldBe 1
        springSeatJpa.findById(seatIds.first()).get().status shouldBe SeatStatus.RESERVED.name
        springSeatJpa.findById(seatIds.last()).get().status shouldBe SeatStatus.AVAILABLE.name
    }

    /** 조건부 UPDATE(@Modifying)는 트랜잭션이 필요하다 — 운영에서는 Facade 트랜잭션 안에서 호출됨 */
    private fun <T> inTx(block: () -> T): T = transactionTemplate.execute { block() }!!

    /** 예약을 만들고 좌석을 그 예약의 HOLD 로 바꾼다 (ReserveSeatService 와 같은 도메인 경로) */
    private fun holdSeats(scheduleId: Long, seatIds: List<Long>): Long {
        val reservation = reservationRepository.save(Reservation.create(MEMBER_ID))
        val seats = seatRepository.findSeats(scheduleId, seatIds)
        seats.forEach { it.reserve(reservation) }
        seatRepository.saveAll(seats)
        return reservation.id
    }

    companion object {
        private const val SEAT_COUNT = 2
        private const val PRICE = 50_000
        private const val MEMBER_ID = 1L
        private const val OTHER_MEMBER_ID = 2L
    }
}
