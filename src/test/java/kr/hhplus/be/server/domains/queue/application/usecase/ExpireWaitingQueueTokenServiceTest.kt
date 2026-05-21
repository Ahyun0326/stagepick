package kr.hhplus.be.server.domains.queue.application.usecase

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class ExpireWaitingQueueTokenServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val waitingQueueRepository: WaitingQueueRepository = mockk()
    val expireWaitingQueueTokenService = ExpireWaitingQueueTokenService(waitingQueueRepository)

    given("대기 큐가 존재하는 스케줄들이 있을 때") {
        every { waitingQueueRepository.findScheduleIds() } returns listOf(1L, 2L)
        every { waitingQueueRepository.removeInactiveWaiting(any()) } just runs

        `when`("비활성 대기열 엔트리를 정리하면") {
            expireWaitingQueueTokenService.execute()

            then("스케줄별 대기 큐에서 heartbeat가 끊긴 엔트리를 제거한다") {
                verify(exactly = 1) { waitingQueueRepository.removeInactiveWaiting(1L) }
                verify(exactly = 1) { waitingQueueRepository.removeInactiveWaiting(2L) }
            }
        }
    }
})
