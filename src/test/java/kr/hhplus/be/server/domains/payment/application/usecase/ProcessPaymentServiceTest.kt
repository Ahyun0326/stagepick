package kr.hhplus.be.server.domains.payment.application.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kr.hhplus.be.server.common.exception.DuplicatePaymentException
import kr.hhplus.be.server.common.exception.InsufficientPointException
import kr.hhplus.be.server.common.exception.NegativePointException
import kr.hhplus.be.server.common.exception.PaymentAmountMatchException
import kr.hhplus.be.server.common.exception.PointNotFoundException
import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.common.exception.ReservationSeatExpiredException
import kr.hhplus.be.server.domains.common.lock.LockManager
import kr.hhplus.be.server.domains.common.transaction.AfterCommitExecutor
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.domain.model.Payment
import kr.hhplus.be.server.domains.payment.domain.model.PaymentStatus
import kr.hhplus.be.server.domains.payment.domain.repository.PaymentRepository
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import java.time.LocalDateTime
import java.util.Optional

class ProcessPaymentServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val reservationRepository: ReservationRepository = mockk()
    val seatRepository: SeatRepository = mockk()
    val seatHoldRepository: SeatHoldRepository = mockk()
    val pointRepository: PointRepository = mockk()
    val pointHistoryRepository: PointHistoryRepository = mockk()
    val paymentRepository: PaymentRepository = mockk()
    val pointValidator: PointValidator = mockk()
    val lockManager: LockManager = mockk()
    val afterCommitExecutor: AfterCommitExecutor = mockk()
    val memberId = 1L

    val processPaymentService = ProcessPaymentService(
        reservationRepository,
        seatRepository,
        seatHoldRepository,
        pointRepository,
        pointHistoryRepository,
        paymentRepository,
        pointValidator,
        lockManager,
        afterCommitExecutor
    )

    // 전역 전제 조건: 모든 테스트에서 락 매니저는 항상 람다를 즉시 실행한다
    every {
        lockManager.runWithLock<Any?>(any(), any(), any(), any())
    } answers {
        val args = it.invocation.args
        val block = args.last() as () -> Any?
        block.invoke()
    }

    // 전역 전제 조건: 커밋 이후 실행기는 작업을 즉시 실행한다 (커밋 시점 동작은 통합 테스트에서 검증)
    every { afterCommitExecutor.execute(any()) } answers { firstArg<() -> Unit>().invoke() }

    given("이미 결제된 예약 ID로") {
        val reservationId = 1L
        val point = 1000
        val payment = mockk<Payment>()
        val request = PaymentRequest(reservationId, point, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns payment

        `when`("결제 요청이 들어오면") {
            then("DuplicatePaymentException이 발생한다") {
                shouldThrow<DuplicatePaymentException> {
                    processPaymentService.process(memberId, request)
                }
            }
        }
    }

    // 0/음수 경계값 검증은 PointValidatorTest에서 담당 → 여기서는 validator 예외가 전파되는 행동만 검증
    given("유효하지 않은 포인트(0 또는 음수)로") {
        val reservationId = 1L
        val point = 0
        val request = PaymentRequest(reservationId, point, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(point) } throws NegativePointException()

        `when`("결제 요청이 들어오면") {
            then("NegativePointException이 발생한다") {
                shouldThrow<NegativePointException> {
                    processPaymentService.process(memberId, request)
                }
            }
        }
    }

    given("존재하지 않는 예약 ID로") {
        val reservationId = 1L
        val point = 1000
        val request = PaymentRequest(reservationId, point, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(point) } just runs
        every { reservationRepository.findById(reservationId) } returns null

        `when`("결제 요청이 들어오면") {
            then("ReservationNotFoundException 발생한다") {
                shouldThrow<ReservationNotFoundException> {
                    processPaymentService.process(memberId, request)
                }

            }
        }
    }

    given("다른 회원의 예약 ID로") {
        val reservationId = 1L
        val point = 1000
        val request = PaymentRequest(reservationId, point, scheduleId = 10L)
        val otherMemberReservation = Reservation("test", 2L).apply { assignId(reservationId) }

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(point) } just runs
        every { reservationRepository.findById(reservationId) } returns otherMemberReservation

        `when`("결제 요청이 들어오면") {
            then("ReservationNotFoundException 발생한다") {
                shouldThrow<ReservationNotFoundException> {
                    processPaymentService.process(memberId, request)
                }
            }
        }
    }

    given("예약 정보로 조회한 좌석 목록이 비어 있을 때 (유효 시간 만료)") {
        val reservationId = 1L
        val point = 1000
        val reservation = Reservation("test", memberId).apply { assignId(reservationId) }

        val request = PaymentRequest(reservationId, point, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(point) } just runs
        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns emptyList()

        `when`("결제 요청이 들어오면") {
            then("ReservationSeatExpiredException이 발생한다") {
                shouldThrow<ReservationSeatExpiredException> {
                    processPaymentService.process(memberId, request)
                }
            }
        }
    }

    given("좌석은 아직 예약에 연결돼 있지만 홀드 만료 시각이 지났을 때 (스케줄러 해제 전)") {
        val reservationId = 1L
        val point = 1000
        val reservation = Reservation("test", memberId).apply { assignId(reservationId) }
        val seats = Seat(1L, 1L, "A1", SeatStatus.HOLD.name, point)

        val request = PaymentRequest(reservationId, point, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(point) } just runs
        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns listOf(seats)
        every { seatHoldRepository.isAllHoldActive(listOf(seats.id)) } returns false

        `when`("결제 요청이 들어오면") {
            then("ReservationSeatExpiredException이 발생하고 좌석 상태는 바뀌지 않는다") {
                shouldThrow<ReservationSeatExpiredException> {
                    processPaymentService.process(memberId, request)
                }

                verify(exactly = 0) { seatRepository.confirmHeldSeats(any(), any()) }
                verify(exactly = 0) { seatHoldRepository.removeSeats(any()) }
            }
        }
    }

    given("검증은 통과했지만 결제 확정 직전에 만료 처리가 좌석을 먼저 해제했을 때") {
        val reservationId = 1L
        val usagePoint = 1000
        val reservation = Reservation("test", memberId).apply { assignId(reservationId) }
        val seats = Seat(1L, 1L, "A1", SeatStatus.HOLD.name, usagePoint)
        val point = Point(1L, memberId, usagePoint)

        val request = PaymentRequest(reservationId, usagePoint, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(usagePoint) } just runs
        every { pointValidator.validatePaymentAmountMatch(usagePoint, usagePoint) } just runs
        every { pointValidator.validateInsufficientPoint(usagePoint, usagePoint) } just runs
        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns listOf(seats)
        every { seatHoldRepository.isAllHoldActive(listOf(seats.id)) } returns true
        every { pointRepository.findPointByMemberId(any()) } returns Optional.of(point)
        // 조건부 UPDATE(HOLD + 해당 예약)가 0건 → 체크와 쓰기 사이 경합
        every { seatRepository.confirmHeldSeats(listOf(seats.id), reservationId) } returns 0

        `when`("결제 요청이 들어오면") {
            then("ReservationSeatExpiredException이 발생하고 포인트·결제·Redis 정리는 실행되지 않는다") {
                shouldThrow<ReservationSeatExpiredException> {
                    processPaymentService.process(memberId, request)
                }

                verify(exactly = 0) { pointRepository.save(any()) }
                verify(exactly = 0) { paymentRepository.save(any(), any()) }
                verify(exactly = 0) { afterCommitExecutor.execute(any()) }
            }
        }
    }

    given("회원의 포인트가 존재하지 않을 때") {
        val reservationId = 1L
        val point = 1000
        val reservation = Reservation("test", memberId).apply { assignId(reservationId) }
        val seats = Seat(1L, 1L, "A1", SeatStatus.AVAILABLE.name, 1000)

        val request = PaymentRequest(reservationId, point, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(point) } just runs
        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns listOf(seats)
        every { seatHoldRepository.isAllHoldActive(listOf(seats.id)) } returns true
        every { pointRepository.findPointByMemberId(any()) } returns Optional.empty<Point>()

        `when`("결제 요청이 들어오면") {
            then("PointNotFoundException이 발생한다") {
                shouldThrow<PointNotFoundException> {
                    processPaymentService.process(memberId, request)
                }
            }
        }
    }

    given("회원의 포인트가 결제 금액과 일치하지 않을 때") {
        val reservationId = 1L
        val usagePoint = 1000
        val paymentAmount = 1100

        val reservation = Reservation("test", memberId).apply { assignId(reservationId) }
        val seats = Seat(1L, 1L, "A1", SeatStatus.AVAILABLE.name, paymentAmount)
        val point = Point(1L, memberId, 1000)

        val request = PaymentRequest(reservationId, usagePoint, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(usagePoint) } just runs
        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns listOf(seats)
        every { seatHoldRepository.isAllHoldActive(listOf(seats.id)) } returns true
        every { pointRepository.findPointByMemberId(any()) } returns Optional.of(point)
        every {
            pointValidator.validatePaymentAmountMatch(
                paymentAmount,
                usagePoint
            )
        } throws PaymentAmountMatchException()

        `when`("결제 요청이 들어오면") {
            then("PaymentAmountMatchException이 발생한다") {
                shouldThrow<PaymentAmountMatchException> {
                    processPaymentService.process(memberId, request)
                }
            }
        }
    }

    given("회원의 포인트가 결제 금액보다 부족할 때") {
        val reservationId = 1L
        val usagePoint = 1000
        val paymentAmount = 1000
        val memberBalance = 900

        val reservation = Reservation("test", memberId).apply { assignId(reservationId) }
        val seats = Seat(1L, 1L, "A1", SeatStatus.AVAILABLE.name, paymentAmount)
        val point = Point(1L, memberId, memberBalance)

        val request = PaymentRequest(reservationId, usagePoint, scheduleId = 10L)

        every { paymentRepository.findByReservationId(reservationId) } returns null
        every { pointValidator.validateNegativePoint(usagePoint) } just runs
        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns listOf(seats)
        every { seatHoldRepository.isAllHoldActive(listOf(seats.id)) } returns true
        every { pointRepository.findPointByMemberId(any()) } returns Optional.of(point)
        every { pointValidator.validatePaymentAmountMatch(paymentAmount, usagePoint) } just runs
        every {
            pointValidator.validateInsufficientPoint(
                memberBalance,
                usagePoint
            )
        } throws InsufficientPointException()

        `when`("결제 요청이 들어오면") {
            then("InsufficientPointException이 발생한다") {
                shouldThrow<InsufficientPointException> {
                    processPaymentService.process(memberId, request)
                }
            }
        }
    }

    given("유효한 예약 ID와 충분한 포인트로") {
        val reservationId = 1L
        val usagePoint = 1000
        val paymentAmount = 1000
        val memberBalance = 1000
        val concertedAt = LocalDateTime.of(2026, 1, 1, 1, 1)

        val reservation = Reservation("test", memberId).apply { assignId(reservationId) }
        val seats = Seat(1L, 1L, "A1", SeatStatus.AVAILABLE.name, paymentAmount)
        val point = Point(1L, memberId, memberBalance)

        val reservationPaymentDetailQueryDto = ReservationPaymentDetailQueryDto(
            reservationId = reservationId,
            title = "concert1",
            location = "location1",
            viewingTime = 2,
            datetime = concertedAt,
            seatNumbers = listOf(seats.number),
            price = paymentAmount
        )

        val payment = Payment(reservationId, "test", paymentAmount, PaymentStatus.PAID.name, mockk())

        val request = PaymentRequest(reservationId, usagePoint, scheduleId = 10L)

        // 검증 단계
        every { paymentRepository.findByReservationId(any()) } returns null
        every { pointValidator.validateNegativePoint(usagePoint) } just runs
        every { pointValidator.validatePaymentAmountMatch(paymentAmount, usagePoint) } just runs
        every { pointValidator.validateInsufficientPoint(memberBalance, usagePoint) } just runs

        // 조회 단계
        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns listOf(seats)
        every { seatHoldRepository.isAllHoldActive(listOf(seats.id)) } returns true
        every { pointRepository.findPointByMemberId(any()) } returns Optional.of(point)
        every { reservationRepository.getWithDetailsById(reservationId) } returns reservationPaymentDetailQueryDto

        // 저장 단계
        every { seatRepository.confirmHeldSeats(listOf(seats.id), reservationId) } returns 1
        every { seatHoldRepository.removeSeats(any()) } just runs
        every { pointRepository.save(any()) } returns point
        every { pointHistoryRepository.save(any()) } returns mockk()
        every { paymentRepository.save(any(), reservation) } returns payment

        `when`("결제 요청이 들어오면") {
            then("포인트가 차감되고 결제 내역이 저장된다") {
                processPaymentService.process(memberId, request)

                verify(exactly = 1) { pointRepository.save(point) }
                verify(exactly = 1) { paymentRepository.save(any(), any()) }
            }

            then("Redis 홀드 제거는 커밋 이후 실행기를 통해 위임된다") {
                processPaymentService.process(memberId, request)

                verify(exactly = 1) { afterCommitExecutor.execute(any()) }
                verify(exactly = 1) { seatHoldRepository.removeSeats(listOf(seats.id)) }
            }

            then("결제 응답이 정상적으로 반환된다.") {
                val result = processPaymentService.process(memberId, request)

                result.shouldNotBeNull {
                    this.paymentId shouldBe payment.id
                    this.paymentNumber shouldBe payment.number
                    this.amount shouldBe payment.price
                    this.status shouldBe payment.status
                    this.paidAt shouldBe payment.createdAt
                }
            }
        }
    }

})
