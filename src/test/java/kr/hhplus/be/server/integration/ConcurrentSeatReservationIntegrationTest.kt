package kr.hhplus.be.server.integration

import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.queue.application.facade.QueueFacade
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.facade.ReservationFacade
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SpringSeatJpa
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 필수 통합 테스트 ③ - 여러 사용자가 같은 좌석을 동시에 요청하면 한 명만 성공한다
 *
 * 모든 스레드를 출발선(startGate)에 세운 뒤 동시에 출발시켜 실제 경합을 만든다.
 * 방어선은 두 겹: ① Redisson 분산락(schedule:{id}:seat-reservation) ② AVAILABLE 일 때만 HOLD 로 바꾸는 조건부 UPDATE.
 * DB 방어선 단독 검증은 SeatRepositoryIntegrationTest 에서 락 없이 수행한다.
 */
@DisplayName("[필수 통합] 동시 좌석 요청 시 한 명만 성공")
class ConcurrentSeatReservationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture
    @Autowired private lateinit var queueFacade: QueueFacade
    @Autowired private lateinit var reservationFacade: ReservationFacade
    @Autowired private lateinit var springSeatJpa: SpringSeatJpa
    @Autowired private lateinit var seatHoldRepository: SeatHoldRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `같은 좌석에 동시에 예약 요청이 몰려도 한 명만 성공하고 예약도 하나만 남는다`() {
        // given - 좌석 1개, 토큰을 받은 사용자 N명
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(SEAT_COUNT, SEAT_PRICE)
        val uuids = (1..USER_COUNT).map {
            val member = fixture.createMember()
            val token = queueFacade.issueToken(member.uuid, scheduleId).token.shouldNotBeNull()
            queueFacade.validateToken(member.uuid, scheduleId, token)
            member.uuid
        }

        // when - N명이 같은 순간에 같은 좌석 예약
        val successReservationIds = Collections.synchronizedList(mutableListOf<Long>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        runConcurrently(uuids) { uuid ->
            runCatching { reservationFacade.reserveSeat(uuid, ReservationRequest(scheduleId, seatIds)) }
                .onSuccess { successReservationIds += it.reservationId }
                .onFailure { failures += it }
        }

        // then 1 - 정확히 한 명만 성공, 나머지는 모두 실패
        successReservationIds shouldHaveSize 1
        failures shouldHaveSize USER_COUNT - 1

        // then 2 - 좌석은 성공한 예약의 HOLD, Redis 홀드도 등록
        val winnerReservationId = successReservationIds.single()
        springSeatJpa.findAllById(seatIds).forAll {
            it.status shouldBe SeatStatus.HOLD.name
            it.reservationId shouldBe winnerReservationId
        }
        seatHoldRepository.getHeldStatus(seatIds).forAll { it shouldBe true }

        // then 3 - 실패한 요청의 예약 행은 남지 않는다 (좌석 없는 고아 예약 없음)
        countReservations() shouldBe 1
    }

    /** 모든 작업을 출발선에 세운 뒤 동시에 시작시키고, 끝날 때까지 기다린다 */
    private fun <T> runConcurrently(inputs: List<T>, task: (T) -> Unit) {
        val executor = Executors.newFixedThreadPool(inputs.size)
        val ready = CountDownLatch(inputs.size)
        val startGate = CountDownLatch(1)
        val done = CountDownLatch(inputs.size)

        inputs.forEach { input ->
            executor.submit {
                ready.countDown()
                startGate.await()
                try {
                    task(input)
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await(AWAIT_SECONDS, TimeUnit.SECONDS)
        startGate.countDown()
        done.await(AWAIT_SECONDS, TimeUnit.SECONDS) shouldBe true
        executor.shutdown()
    }

    private fun countReservations(): Int =
        jdbcTemplate.queryForObject(COUNT_RESERVATIONS_SQL, Int::class.java) ?: 0

    companion object {
        private const val SEAT_COUNT = 1
        private const val SEAT_PRICE = 50_000
        private const val USER_COUNT = 10
        private const val AWAIT_SECONDS = 30L
        private const val COUNT_RESERVATIONS_SQL = "SELECT COUNT(*) FROM reservation"
    }
}
