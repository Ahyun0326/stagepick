package kr.hhplus.be.server.domains.seat.application.usecase

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class ExpireSeatHoldServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val seatRepository: SeatRepository = mockk()
    val seatHoldRepository: SeatHoldRepository = mockk()

    val expireSeatHoldService = ExpireSeatHoldService(seatRepository, seatHoldRepository)

    given("만료된 좌석이 없을 때") {
        every { seatHoldRepository.getExpiredSeats() } returns emptyList()

        `when`("만료 처리를 실행하면") {
            expireSeatHoldService.processExpiredSeats()

            then("좌석 상태 변경과 임시 배정된 좌석 제거가 호출되지 않는다") {
                verify(exactly = 0) { seatRepository.updateStatusToAvailable(any()) }
                verify(exactly = 0) { seatHoldRepository.removeSeats(any()) }
            }
        }
    }

    given("만료된 좌석이 있을 때") {
        val expiredSeatIds = listOf(1L, 2L)

        every { seatHoldRepository.getExpiredSeats() } returns expiredSeatIds
        every { seatRepository.updateStatusToAvailable(expiredSeatIds) } just runs
        every { seatHoldRepository.removeSeats(expiredSeatIds) } just runs

        `when`("만료 처리를 실행하면") {
            expireSeatHoldService.processExpiredSeats()

            then("만료된 좌석이 AVAILABLE로 변경되고 임시 배정된 좌석이 제거된다") {
                verify(exactly = 1) { seatRepository.updateStatusToAvailable(expiredSeatIds) }
                verify(exactly = 1) { seatHoldRepository.removeSeats(expiredSeatIds) }
            }
        }
    }

})
