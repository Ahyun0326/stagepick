package kr.hhplus.be.server.domains.seat.application.usecase

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kr.hhplus.be.server.domains.common.transaction.AfterCommitExecutor
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class ExpireSeatHoldServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val seatRepository: SeatRepository = mockk()
    val seatHoldRepository: SeatHoldRepository = mockk()
    val afterCommitExecutor: AfterCommitExecutor = mockk()

    val expireSeatHoldService = ExpireSeatHoldService(seatRepository, seatHoldRepository, afterCommitExecutor)

    // 커밋 이후 실행기는 작업을 즉시 실행한다 (커밋 시점 동작은 통합 테스트에서 검증)
    every { afterCommitExecutor.execute(any()) } answers { firstArg<() -> Unit>().invoke() }

    given("만료된 좌석이 없을 때") {
        every { seatHoldRepository.getExpiredSeats() } returns emptyList()

        `when`("만료 처리를 실행하면") {
            expireSeatHoldService.processExpiredSeats()

            then("좌석 상태 변경과 임시 배정된 좌석 제거가 호출되지 않는다") {
                verify(exactly = 0) { seatRepository.releaseHeldSeats(any()) }
                verify(exactly = 0) { seatHoldRepository.removeExpiredSeats(any()) }
            }
        }
    }

    given("만료된 좌석이 있을 때") {
        val expiredSeatIds = listOf(1L, 2L)

        every { seatHoldRepository.getExpiredSeats() } returns expiredSeatIds
        every { seatRepository.releaseHeldSeats(expiredSeatIds) } returns expiredSeatIds.size
        every { seatHoldRepository.removeExpiredSeats(expiredSeatIds) } just runs

        `when`("만료 처리를 실행하면") {
            expireSeatHoldService.processExpiredSeats()

            then("HOLD 인 좌석만 해제하고, 커밋 이후 아직 만료 상태인 홀드만 제거한다") {
                verify(exactly = 1) { seatRepository.releaseHeldSeats(expiredSeatIds) }
                verify(exactly = 1) { afterCommitExecutor.execute(any()) }
                verify(exactly = 1) { seatHoldRepository.removeExpiredSeats(expiredSeatIds) }
            }
        }
    }

})
