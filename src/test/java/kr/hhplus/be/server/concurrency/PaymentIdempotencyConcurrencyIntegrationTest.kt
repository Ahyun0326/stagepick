package kr.hhplus.be.server.concurrency

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.common.exception.DuplicatePaymentException
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.facade.PaymentFacade
import kr.hhplus.be.server.domains.payment.domain.model.Payment
import kr.hhplus.be.server.domains.payment.domain.model.PaymentLog
import kr.hhplus.be.server.domains.payment.domain.repository.PaymentRepository
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.facade.ReservationFacade
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import kr.hhplus.be.server.support.runConcurrently
import kr.hhplus.be.server.support.runConcurrentlyCollecting
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * STEP4 선택 심화 ② 동시성 리스트업 — 결제 멱등성 (같은 예약 중복 결제)
 *
 * 리스트업 근거: 결제는 `reservation:{id}:payment` 분산락 안에서 `findByReservationId`로 중복을
 * 검사한다. 락은 커밋 이후에 해제되므로(같은 예약에 대한 동시 요청은 완전히 직렬화됨) 정상적으로는
 * 중복이 생기지 않아야 하지만, `payment.reservation_id`에는 UNIQUE 제약이 없었고 락의
 * leaseTime(3초)이 고정값이라는 구조적 한계가 있다 (docs/agent/handoff.md에 기록된 기존 지적).
 *
 * 방어선: `payment.reservation_id` UNIQUE 제약 + `PaymentJpaRepository.save()`에서
 * 제약 위반을 `DuplicatePaymentException`으로 변환 — "DB가 마지막 방어선" 원칙에 따라
 * 락 레벨 방어와 무관하게 DB 단독으로도 중복 결제 행이 생기지 않도록 한다.
 */
@DisplayName("[동시성] 결제 멱등성 (같은 예약 중복 결제)")
class PaymentIdempotencyConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture
    @Autowired private lateinit var reservationFacade: ReservationFacade
    @Autowired private lateinit var paymentFacade: PaymentFacade
    @Autowired private lateinit var paymentRepository: PaymentRepository
    @Autowired private lateinit var reservationRepository: ReservationRepository
    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `같은 예약을 동시에 여러 번 결제해도 분산락 덕분에 정확히 한 번만 성공한다 (락 방어선 확인 - 재현 안 됨)`() {
        // given
        val member = fixture.createMember()
        fixture.chargePoint(member.id!!, INITIAL_BALANCE)
        val (scheduleId, seatIds) = fixture.createScheduleWithSeats(1, SEAT_PRICE)
        val reservationId =
            reservationFacade.reserveSeat(member.uuid, ReservationRequest(scheduleId, seatIds)).reservationId

        val successCount = AtomicInteger(0)
        val duplicateCount = AtomicInteger(0)
        val otherFailures = Collections.synchronizedList(mutableListOf<Throwable>())

        // when - 같은 예약을 ATTEMPT_COUNT개 스레드가 같은 순간에 결제 시도
        runConcurrently((1..ATTEMPT_COUNT).toList()) {
            runCatching { paymentFacade.processPayment(member.uuid, PaymentRequest(reservationId, SEAT_PRICE, scheduleId)) }
                .onSuccess { successCount.incrementAndGet() }
                .onFailure { e ->
                    if (e is DuplicatePaymentException) duplicateCount.incrementAndGet() else otherFailures += e
                }
        }

        // then - 분산락이 커밋 이후에만 풀리므로 요청이 완전히 직렬화되어 정확히 1번만 성공한다
        otherFailures.shouldBeEmpty()
        successCount.get() shouldBe 1
        duplicateCount.get() shouldBe ATTEMPT_COUNT - 1
        paymentRepository.findByReservationId(reservationId).shouldNotBeNull()
    }

    @Test
    fun `분산락 없이 저장소를 직접 호출하면 두 번째 결제 저장은 DB UNIQUE 제약으로 막힌다 (DB 단독 방어선)`() {
        // given - 락을 거치지 않고 저장소를 직접 두 번 호출 (lock leaseTime 만료 등으로 락이 뚫렸다고 가정)
        val reservation = inTx { reservationRepository.save(Reservation.create(MEMBER_ID)) }
        val paymentLog = PaymentLog("테스트유저", "테스트 콘서트", "테스트 공연장", LocalDateTime.now(), 120, listOf("A1"), SEAT_PRICE)

        // when 1 - 첫 번째 결제 저장은 성공
        inTx { paymentRepository.save(Payment.create(reservation.id, SEAT_PRICE, paymentLog), reservation) }

        // when 2 - 같은 reservationId로 두 번째 결제 저장
        val secondAttempt = { inTx { paymentRepository.save(Payment.create(reservation.id, SEAT_PRICE, paymentLog), reservation) } }

        // then - UNIQUE(reservation_id) 위반이 DuplicatePaymentException으로 변환된다
        shouldThrow<DuplicatePaymentException> { secondAttempt() }
        paymentRepository.findByReservationId(reservation.id).shouldNotBeNull()
    }

    @Test
    fun `분산락 없이 N개 트랜잭션이 동시에 같은 예약을 결제 저장해도 DB는 한 건만 허용한다`() {
        // given - 예약 1건, 락을 거치지 않고 저장소를 직접 동시에 호출
        val reservation = inTx { reservationRepository.save(Reservation.create(MEMBER_ID)) }
        val paymentLog = PaymentLog("테스트유저", "테스트 콘서트", "테스트 공연장", LocalDateTime.now(), 120, listOf("A1"), SEAT_PRICE)

        // when
        val results = runConcurrentlyCollecting((1..CONCURRENT_COUNT).toList()) {
            runCatching {
                inTx { paymentRepository.save(Payment.create(reservation.id, SEAT_PRICE, paymentLog), reservation) }
            }
        }

        // then - 정확히 한 트랜잭션만 성공, 나머지는 모두 DuplicatePaymentException
        results.count { it.isSuccess } shouldBe 1
        results.filter { it.isFailure }.forEach { result ->
            result.exceptionOrNull().shouldBeInstanceOf<DuplicatePaymentException>()
        }
    }

    /** 조건부 UPDATE/저장은 트랜잭션이 필요하다 — 운영에서는 Facade의 @Transactional 안에서 호출됨 */
    private fun <T> inTx(block: () -> T): T = transactionTemplate.execute { block() }!!

    companion object {
        private const val SEAT_PRICE = 50_000
        private const val INITIAL_BALANCE = 1_000_000
        // 분산락을 거쳐 전체 결제 플로우(DB+Redis 여러 번 왕복)를 직렬화하는 테스트.
        // 원래 의도한 동시 결제 수준(10)으로 둔다 — HikariCP maximum-pool-size를
        // 충분히 늘려 커넥션 고갈 없이 통과하는지 확인한다(docs/report/step4-concurrency-report.md §10).
        private const val ATTEMPT_COUNT = 10
        private const val CONCURRENT_COUNT = 10
        private const val MEMBER_ID = 1L
    }
}
