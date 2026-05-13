package kr.hhplus.be.server.domains.queue.application.usecase

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class PromoteQueueTokenServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val activeQueueRepository: ActiveQueueRepository = mockk()
    val waitingQueueRepository: WaitingQueueRepository = mockk()

    given("활성 슬롯이 입장 허용 수보다 적게 남아 있을 때") {
        val promoteQueueTokenService = PromoteQueueTokenService(
            activeQueueRepository,
            waitingQueueRepository,
            capacity = 1000,
            admissionRatePerTick = 30
        )

        every { activeQueueRepository.countActive() } returns 990
        every { waitingQueueRepository.popWaiting(10) } returns listOf("uuid-1", "uuid-2")
        every { activeQueueRepository.saveActive(any()) } returns "token"

        `when`("대기자를 승격하면") {
            promoteQueueTokenService.execute()

            then("남은 슬롯 수만큼만 대기자를 꺼낸다") {
                verify(exactly = 1) { waitingQueueRepository.popWaiting(10) }
                verify(exactly = 2) { activeQueueRepository.saveActive(any()) }
            }
        }
    }

    given("활성 슬롯이 입장 허용 수보다 많이 남아 있을 때") {
        val promoteQueueTokenService = PromoteQueueTokenService(
            activeQueueRepository,
            waitingQueueRepository,
            capacity = 1000,
            admissionRatePerTick = 30
        )

        every { activeQueueRepository.countActive() } returns 900
        every { waitingQueueRepository.popWaiting(30) } returns listOf("uuid-1", "uuid-2", "uuid-3")
        every { activeQueueRepository.saveActive(any()) } returns "token"

        `when`("대기자를 승격하면") {
            promoteQueueTokenService.execute()

            then("tick당 입장 허용 수만큼만 대기자를 꺼낸다") {
                verify(exactly = 1) { waitingQueueRepository.popWaiting(30) }
                verify(exactly = 3) { activeQueueRepository.saveActive(any()) }
            }
        }
    }

    given("활성 슬롯이 남아 있지 않을 때") {
        val promoteQueueTokenService = PromoteQueueTokenService(
            activeQueueRepository,
            waitingQueueRepository,
            capacity = 1000,
            admissionRatePerTick = 30
        )

        every { activeQueueRepository.countActive() } returns 1000

        `when`("대기자를 승격하면") {
            promoteQueueTokenService.execute()

            then("대기열에서 사용자를 꺼내지 않는다") {
                verify(exactly = 0) { waitingQueueRepository.popWaiting(any()) }
                verify(exactly = 0) { activeQueueRepository.saveActive(any()) }
            }
        }
    }
})
