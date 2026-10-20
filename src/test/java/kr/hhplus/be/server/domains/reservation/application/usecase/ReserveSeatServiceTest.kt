package kr.hhplus.be.server.domains.reservation.application.usecase

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
import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.common.exception.SeatNotFoundException
import kr.hhplus.be.server.common.exception.SeatUnavailableException
import kr.hhplus.be.server.domains.common.lock.LockManager
import kr.hhplus.be.server.domains.reservation.application.dto.ReservationRequest
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.application.validator.SeatValidator
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class ReserveSeatServiceTest : BehaviorSpec({
    // then 마다 given/when 새로 실행하도록 설정
    isolationMode = IsolationMode.InstancePerLeaf

    val scheduleRepository: ScheduleRepository = mockk()
    val seatRepository: SeatRepository = mockk()
    val reservationRepository: ReservationRepository = mockk()
    val seatHoldRepository: SeatHoldRepository = mockk()
    val seatValidator: SeatValidator = mockk()
    val lockManager: LockManager = mockk()
    val memberId = 1L

    val reserveSeatService = ReserveSeatService(
        scheduleRepository,
        seatRepository,
        reservationRepository,
        seatHoldRepository,
        seatValidator,
        lockManager
    )

    // 모든 given/when 블록보다 먼저 실행 → lockManager는 항상 람다를 실행하도록 설정
    every {
        lockManager.runWithLock<Any?>(any(), any(), any(), any())
    } answers {
        val args = it.invocation.args
        val block = args.last() as () -> Any?
        block.invoke()
    }

    given("존재하지 않는 공연 회차 ID로") {
        val scheduleId = 1L
        val request = ReservationRequest(scheduleId, emptyList())

        every { scheduleRepository.existsById(scheduleId) } returns false

        `when`("좌석 예약을 요청하면") {

            then("ScheduleNotFoundException이 발생한다") {
                shouldThrow<ScheduleNotFoundException> {
                    reserveSeatService.invoke(memberId, request)
                }
            }
        }
    }

    given("schedule은 존재하지만 좌석 ID 리스트가 비어있는 경우") {
        val scheduleId = 1L
        val request = ReservationRequest(scheduleId, emptyList())

        every { scheduleRepository.existsById(scheduleId) } returns true
        every { seatRepository.findSeats(scheduleId, emptyList()) } returns emptyList()
        every { seatValidator.validateExistingSeats(any(), any()) } throws SeatNotFoundException(emptyList())

        `when` ("좌석 예약을 요청하면") {

            then("SeatNotFoundException이 발생한다") {
                shouldThrow<SeatNotFoundException> {
                    reserveSeatService.invoke(memberId, request)
                }
            }
        }
    }

    given("존재하지 않는 좌석 ID가 포함된 리스트로") {
        val scheduleId = 1L
        val seatIds = listOf(1L, 2L)
        val request = ReservationRequest(scheduleId, seatIds)

        every { scheduleRepository.existsById(scheduleId) } returns true
        every { seatRepository.findSeats(scheduleId, seatIds) } returns emptyList()
        every { seatValidator.validateExistingSeats(any(), any()) } throws SeatNotFoundException(emptyList())

        `when`("좌석 예약을 요청하면") {

            then("SeatNotFoundException이 발생한다") {
                shouldThrow<SeatNotFoundException> {
                    reserveSeatService.invoke(memberId, request)
                }
            }
        }
    }

    given("이미 예약된 좌석 ID가 포함된 리스트로") {
        val scheduleId = 1L
        val seatIds = listOf(1L, 2L)
        val request = ReservationRequest(scheduleId, seatIds)

        val unavailableSeats = listOf(
            Seat(id = 1L, scheduleId = scheduleId, number = "A1", status = SeatStatus.HOLD.name, price = 50000),
            Seat(id = 2L, scheduleId = scheduleId, number = "A2", status = SeatStatus.RESERVED.name, price = 50000)
        )

        every { scheduleRepository.existsById(scheduleId) } returns true
        every { seatRepository.findSeats(scheduleId, seatIds) } returns unavailableSeats
        every { seatValidator.validateExistingSeats(any(), any()) } just runs
        every { seatValidator.validateAvailableSeats(any()) } throws SeatUnavailableException(seatIds)

        `when`("좌석 예약을 요청하면") {
            then("SeatUnavailableException이 발생한다") {
                shouldThrow<SeatUnavailableException> {
                    reserveSeatService.invoke(memberId, request)
                }
            }
        }
    }

    given("유효한 공연 회차 ID, 좌석 ID 리스트로") {
        val scheduleId = 1L
        val reservation = Reservation("test", memberId)

        // 케이스별로 다른 seatIds/seats는 각 when 블록에서 정의
        every { scheduleRepository.existsById(scheduleId) } returns true
        every { seatValidator.validateExistingSeats(any(), any()) } just runs
        every { seatValidator.validateAvailableSeats(any()) } just runs
        every { reservationRepository.save(any()) } returns reservation
        every { seatHoldRepository.hold(any()) } just runs

        `when`("좌석 2개를 예약하면") {
            val seatIds = listOf(1L, 2L)
            val seats = listOf(
                Seat(id = 1L, scheduleId = scheduleId, number = "A1", status = SeatStatus.AVAILABLE.name, price = 50000),
                Seat(id = 2L, scheduleId = scheduleId, number = "A2", status = SeatStatus.AVAILABLE.name, price = 50000)
            )

            every { seatRepository.findSeats(scheduleId, seatIds) } returns seats
            every { seatRepository.holdAvailableSeats(seatIds, reservation.id) } returns seatIds.size

            val result = reserveSeatService.invoke(memberId, ReservationRequest(scheduleId, seatIds))

            then("2개 좌석 모두 조건부 UPDATE 로 HOLD 되고 예약된다") {
                verify(exactly = 1) { reservationRepository.save(any()) }
                verify(exactly = 1) { seatRepository.holdAvailableSeats(seatIds, reservation.id) }
                verify(exactly = 1) { seatHoldRepository.hold(any()) }

                result.shouldNotBeNull { this.reservationId shouldBe reservation.id }
            }
        }

        `when`("좌석 1개를 예약하면") {
            val seatIds = listOf(1L)
            val seats = listOf(
                Seat(id = 1L, scheduleId = scheduleId, number = "A1", status = SeatStatus.AVAILABLE.name, price = 50000)
            )

            every { seatRepository.findSeats(scheduleId, seatIds) } returns seats
            every { seatRepository.holdAvailableSeats(seatIds, reservation.id) } returns seatIds.size

            val result = reserveSeatService.invoke(memberId, ReservationRequest(scheduleId, seatIds))

            then("단일 좌석도 정상 예약된다") {
                verify(exactly = 1) { reservationRepository.save(any()) }
                verify(exactly = 1) { seatRepository.holdAvailableSeats(seatIds, reservation.id) }
                verify(exactly = 1) { seatHoldRepository.hold(any()) }

                result.shouldNotBeNull { this.reservationId shouldBe reservation.id }
            }
        }

        `when`("조회 시점엔 AVAILABLE 이었지만 조건부 UPDATE 에서 일부 좌석이 이미 선점됐다면 (락이 뚫린 경합)") {
            val seatIds = listOf(1L, 2L)
            val seats = listOf(
                Seat(id = 1L, scheduleId = scheduleId, number = "A1", status = SeatStatus.AVAILABLE.name, price = 50000),
                Seat(id = 2L, scheduleId = scheduleId, number = "A2", status = SeatStatus.AVAILABLE.name, price = 50000)
            )

            every { seatRepository.findSeats(scheduleId, seatIds) } returns seats
            every { seatRepository.holdAvailableSeats(seatIds, reservation.id) } returns 1

            then("SeatUnavailableException 이 발생하고 Redis 홀드는 등록되지 않는다") {
                shouldThrow<SeatUnavailableException> {
                    reserveSeatService.invoke(memberId, ReservationRequest(scheduleId, seatIds))
                }

                verify(exactly = 0) { seatHoldRepository.hold(any()) }
            }
        }
    }
})
