package kr.hhplus.be.server.concurrency

import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.facade.PaymentFacade
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.facade.PointFacade
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.application.facade.ReservationFacade
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import kr.hhplus.be.server.support.runConcurrently
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * STEP4 선택 심화 ② 동시성 리스트업 — 포인트 충전/차감
 *
 * 리스트업 근거: `ChargePointService.invoke()`는 어떤 락도 쓰지 않고 포인트 행을
 * read → (메모리에서) 계산 → save()로 그대로 써버린다. `ProcessPaymentService.processPointUsage()`도
 * 분산락을 쓰지만 그 키는 `reservation:{id}:payment`로 **예약 단위**다. 즉
 * - 같은 회원이 동시에 두 번 충전하면 락이 전혀 없어 경합한다.
 * - 같은 회원이 서로 다른 예약 2건을 동시에 결제하면 락 키가 서로 달라(예약마다 다름)
 *   포인트 행 하나를 두고 역시 경합한다.
 * 두 경우 모두 "값을 읽고 메모리에서 계산한 뒤 그대로 저장"하는 lost update 패턴이라
 * 늦게 commit된 트랜잭션이 먼저 commit된 트랜잭션의 변경을 덮어쓸 수 있다.
 *
 * 방어선: `PointRepository.chargePoint`/`usePoint` — DB에 조건부 UPDATE(`point = point ± amount`)를
 * 직접 위임해, 더하기/빼기 연산 자체를 원자적으로 수행한다(마지막 방어선).
 */
@DisplayName("[동시성] 포인트 충전/차감 lost update")
class PointConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture
    @Autowired private lateinit var pointFacade: PointFacade
    @Autowired private lateinit var pointRepository: PointRepository
    @Autowired private lateinit var reservationFacade: ReservationFacade
    @Autowired private lateinit var paymentFacade: PaymentFacade

    @Test
    fun `같은 회원이 동시에 여러 번 충전해도 최종 잔액은 충전 합계와 같다`() {
        // given - 포인트 행이 없는 신규 회원 (최초 충전 경쟁 포함)
        val member = fixture.createMember()

        // when - CHARGE_COUNT개 스레드가 같은 순간에 같은 회원 포인트를 충전
        runConcurrently((1..CHARGE_COUNT).toList()) {
            pointFacade.chargePoint(member.uuid, ChargePointRequest(CHARGE_AMOUNT))
        }

        // then - 잔액은 반드시 "충전 횟수 * 금액"과 같아야 한다. lost update가 있으면 이보다 작게 나온다.
        val expected = CHARGE_COUNT * CHARGE_AMOUNT
        val actual = pointRepository.findPointByMemberId(member.id!!).get().point
        actual shouldBe expected
    }

    @Test
    fun `같은 회원의 서로 다른 예약 2건을 동시에 결제해도 포인트는 두 결제 금액 합만큼만 차감된다`() {
        // given - 회원 1명, 서로 다른 좌석/예약 2건(락 키가 reservation:1:payment, reservation:2:payment로 다름),
        // 두 결제 합보다 넉넉한 초기 포인트
        val member = fixture.createMember()
        fixture.chargePoint(member.id!!, INITIAL_BALANCE)

        val (scheduleId1, seatIds1) = fixture.createScheduleWithSeats(1, SEAT_PRICE)
        val (scheduleId2, seatIds2) = fixture.createScheduleWithSeats(1, SEAT_PRICE)
        val reservationId1 =
            reservationFacade.reserveSeat(member.uuid, ReservationRequest(scheduleId1, seatIds1)).reservationId
        val reservationId2 =
            reservationFacade.reserveSeat(member.uuid, ReservationRequest(scheduleId2, seatIds2)).reservationId

        val requests = listOf(
            PaymentRequest(reservationId1, SEAT_PRICE, scheduleId1),
            PaymentRequest(reservationId2, SEAT_PRICE, scheduleId2)
        )

        // when - 서로 다른 예약(=서로 다른 락 키)을 같은 순간에 결제
        runConcurrently(requests) { request ->
            paymentFacade.processPayment(member.uuid, request)
        }

        // then - 잔액은 두 결제 금액을 모두 뺀 값이어야 한다. lost update가 있으면 한 번만 차감된 값이 남는다.
        val expected = INITIAL_BALANCE - SEAT_PRICE * 2
        val actual = pointRepository.findPointByMemberId(member.id!!).get().point
        actual shouldBe expected
    }

    @Test
    fun `같은 회원이 충전과 결제(차감)를 동시에 수행해도 최종 잔액은 충전-차감 합계와 일치한다`() {
        // given - 회원 1명, 서로 다른 예약 PAYMENT_COUNT건(결제 락 키가 서로 달라 보호되지 않음),
        // 모든 결제를 감당할 수 있는 초기 포인트
        val member = fixture.createMember()
        fixture.chargePoint(member.id!!, INITIAL_BALANCE)

        val paymentRequests = (1..PAYMENT_COUNT).map {
            val (scheduleId, seatIds) = fixture.createScheduleWithSeats(1, SEAT_PRICE)
            val reservationId =
                reservationFacade.reserveSeat(member.uuid, ReservationRequest(scheduleId, seatIds)).reservationId
            PaymentRequest(reservationId, SEAT_PRICE, scheduleId)
        }

        val chargeTasks = (1..CHARGE_COUNT).map { { pointFacade.chargePoint(member.uuid, ChargePointRequest(CHARGE_AMOUNT)) } }
        val paymentTasks = paymentRequests.map { request -> { paymentFacade.processPayment(member.uuid, request) } }

        // when - 충전(+)과 결제 차감(-)을 같은 순간에 수행 (충전은 락 없음, 결제는 예약 단위 락이라
        // 서로를 보호하지 못한다 — 둘 다 결국 같은 point 행을 조건부 UPDATE/UPSERT로 두드린다)
        runConcurrently(chargeTasks + paymentTasks) { it() }

        // then - 잔액은 "초기값 + 충전 합계 - 결제 합계"와 정확히 같아야 한다
        val expected = INITIAL_BALANCE + CHARGE_COUNT * CHARGE_AMOUNT - PAYMENT_COUNT * SEAT_PRICE
        val actual = pointRepository.findPointByMemberId(member.id!!).get().point
        actual shouldBe expected
    }

    companion object {
        private const val CHARGE_COUNT = 15
        private const val CHARGE_AMOUNT = 1_000
        private const val INITIAL_BALANCE = 1_000_000
        private const val SEAT_PRICE = 50_000
        private const val PAYMENT_COUNT = 5
    }
}
