package kr.hhplus.be.server.domains.queue.application.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.common.exception.QueueTokenNotFoundException
import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class GetQueueStatusServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val activeQueueRepository: ActiveQueueRepository = mockk()
    val waitingQueueRepository: WaitingQueueRepository = mockk()
    val getQueueStatusService = GetQueueStatusService(activeQueueRepository, waitingQueueRepository)

    given("스케줄 활성 토큰이 있는 사용자로") {
        val scheduleId = 1L
        val uuid = "member-uuid"
        val token = "queue-token"

        every { activeQueueRepository.findActive(scheduleId, uuid) } returns token
        every { activeQueueRepository.getRemainingSeconds(scheduleId, uuid) } returns 100

        `when`("대기열 상태를 조회하면") {
            val result = getQueueStatusService.getStatus(uuid, scheduleId)

            then("활성 상태를 반환한다") {
                result.scheduleId shouldBe scheduleId
                result.token shouldBe token
                result.status shouldBe AdmissionStatus.ACTIVE.name
                result.remainingSeconds shouldBe 100
            }
        }
    }

    given("스케줄 대기열에 있는 사용자로") {
        val scheduleId = 1L
        val uuid = "member-uuid"

        every { activeQueueRepository.findActive(scheduleId, uuid) } returns null
        every { waitingQueueRepository.isWaiting(scheduleId, uuid) } returns true
        every { waitingQueueRepository.touchHeartbeat(scheduleId, uuid) } returns Unit
        every { waitingQueueRepository.getRank(scheduleId, uuid) } returns 3

        `when`("대기열 상태를 조회하면") {
            val result = getQueueStatusService.getStatus(uuid, scheduleId)

            then("대기 상태를 반환한다") {
                result.scheduleId shouldBe scheduleId
                result.token shouldBe null
                result.status shouldBe AdmissionStatus.WAITING.name
                result.rank shouldBe 3
                verify(exactly = 1) { waitingQueueRepository.touchHeartbeat(scheduleId, uuid) }
            }
        }
    }

    given("스케줄 큐에 등록되지 않은 사용자로") {
        val scheduleId = 1L
        val uuid = "member-uuid"

        every { activeQueueRepository.findActive(scheduleId, uuid) } returns null
        every { waitingQueueRepository.isWaiting(scheduleId, uuid) } returns false

        `when`("대기열 상태를 조회하면") {
            then("QueueTokenNotFoundException이 발생한다") {
                shouldThrow<QueueTokenNotFoundException> {
                    getQueueStatusService.getStatus(uuid, scheduleId)
                }
            }
        }
    }
})
