package kr.hhplus.be.server.domains.seat.application.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class GetSeatsInfoServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val seatRepository: SeatRepository = mockk()
    val scheduleRepository: ScheduleRepository = mockk()
    val seatHoldRepository: SeatHoldRepository = mockk()

    val getSeatsInfoService = GetSeatsInfoService(seatRepository, scheduleRepository, seatHoldRepository)

    given("존재하지 않는 일정 ID로") {
        val scheduleId = 1L

        every { scheduleRepository.existsById(scheduleId) } returns false

        `when`("좌석 정보를 조회하면") {
            then("ScheduleNotFoundException이 발생한다") {
                shouldThrow<ScheduleNotFoundException> {
                    getSeatsInfoService.invoke(scheduleId)
                }
            }
        }
    }

    given("일정 ID와 일정에 해당하는 좌석 정보가 없는 경우") {
        val scheduleId = 1L

        every { scheduleRepository.existsById(scheduleId) } returns true
        every { seatRepository.findSeatsByScheduleId(scheduleId) } returns emptyList()
        every { seatHoldRepository.getHeldStatus(any()) } returns emptyList()

        `when`("좌석 정보를 조회하면") {
            val result = getSeatsInfoService.invoke(scheduleId)

            then("빈 좌석 리스트가 반환된다") {
                result.seats.isEmpty() shouldBe true
            }
        }
    }

    given("일정 ID와 일정에 해당하는 좌석 정보가 있는 경우") {
        val scheduleId = 1L

        val seats = listOf(
            Seat(1L, scheduleId, "A1", SeatStatus.AVAILABLE.name, 5000),
            Seat(2L, scheduleId, "A2", SeatStatus.AVAILABLE.name, 5000)
        )

        val heldStatuses = listOf(true, false)

        every { scheduleRepository.existsById(scheduleId) } returns true
        every { seatRepository.findSeatsByScheduleId(scheduleId) } returns seats
        every { seatHoldRepository.getHeldStatus(any()) } returns heldStatuses

        `when`("좌석 정보를 조회하면") {
            val result = getSeatsInfoService.invoke(scheduleId)

            then("Redis 점유 중인 좌석은 HOLD, 아닌 좌석은 DB 상태로 반환된다") {
                result.seats.size shouldBe seats.size

                result.seats[0].status shouldBe SeatStatus.HOLD.name
                result.seats[1].status shouldBe SeatStatus.AVAILABLE.name
            }
        }
    }
})
