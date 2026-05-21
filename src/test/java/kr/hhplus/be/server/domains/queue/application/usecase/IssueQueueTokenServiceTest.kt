package kr.hhplus.be.server.domains.queue.application.usecase

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus
import kr.hhplus.be.server.domains.queue.domain.policy.AdmissionPolicy
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class IssueQueueTokenServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val waitingQueueRepository: WaitingQueueRepository = mockk()
    val activeQueueRepository: ActiveQueueRepository = mockk()
    val admissionPolicy: AdmissionPolicy = mockk()
    val issueQueueTokenService = IssueQueueTokenService(
        waitingQueueRepository,
        activeQueueRepository,
        admissionPolicy
    )

    given("이미 스케줄 활성 토큰이 있는 사용자로") {
        val scheduleId = 1L
        val uuid = "member-uuid"
        val token = "queue-token"

        every { activeQueueRepository.findActive(scheduleId, uuid) } returns token
        every { activeQueueRepository.getRemainingSeconds(scheduleId, uuid) } returns 100

        `when`("토큰 발급을 요청하면") {
            val result = issueQueueTokenService.issue(uuid, scheduleId)

            then("기존 활성 토큰을 반환한다") {
                result.scheduleId shouldBe scheduleId
                result.token shouldBe token
                result.status shouldBe AdmissionStatus.ACTIVE.name
                result.remainingSeconds shouldBe 100
            }
        }
    }

    given("이미 스케줄 대기열에 있는 사용자로") {
        val scheduleId = 1L
        val uuid = "member-uuid"

        every { activeQueueRepository.findActive(scheduleId, uuid) } returns null
        every { waitingQueueRepository.isWaiting(scheduleId, uuid) } returns true
        every { waitingQueueRepository.touchHeartbeat(scheduleId, uuid) } returns Unit
        every { waitingQueueRepository.getRank(scheduleId, uuid) } returns 3

        `when`("토큰 발급을 요청하면") {
            val result = issueQueueTokenService.issue(uuid, scheduleId)

            then("현재 대기 순번을 반환한다") {
                result.scheduleId shouldBe scheduleId
                result.token shouldBe null
                result.status shouldBe AdmissionStatus.WAITING.name
                result.rank shouldBe 3
                verify(exactly = 1) { waitingQueueRepository.touchHeartbeat(scheduleId, uuid) }
            }
        }
    }

    given("스케줄 활성 슬롯이 남아 있는 신규 사용자로") {
        val scheduleId = 1L
        val uuid = "member-uuid"
        val token = "queue-token"

        every { activeQueueRepository.findActive(scheduleId, uuid) } returns null
        every { waitingQueueRepository.isWaiting(scheduleId, uuid) } returns false
        every { activeQueueRepository.countActive(scheduleId) } returns 10
        every { admissionPolicy.judge(10) } returns AdmissionStatus.ACTIVE
        every { activeQueueRepository.saveActive(scheduleId, uuid) } returns token
        every { activeQueueRepository.getRemainingSeconds(scheduleId, uuid) } returns 100

        `when`("토큰 발급을 요청하면") {
            val result = issueQueueTokenService.issue(uuid, scheduleId)

            then("활성 토큰을 새로 발급한다") {
                result.scheduleId shouldBe scheduleId
                result.token shouldBe token
                result.status shouldBe AdmissionStatus.ACTIVE.name
                verify(exactly = 1) { activeQueueRepository.saveActive(scheduleId, uuid) }
            }
        }
    }

    given("스케줄 활성 슬롯이 가득 찬 신규 사용자로") {
        val scheduleId = 1L
        val uuid = "member-uuid"

        every { activeQueueRepository.findActive(scheduleId, uuid) } returns null
        every { waitingQueueRepository.isWaiting(scheduleId, uuid) } returns false
        every { activeQueueRepository.countActive(scheduleId) } returns 1000
        every { admissionPolicy.judge(1000) } returns AdmissionStatus.WAITING
        every { waitingQueueRepository.add(scheduleId, uuid) } returns Unit
        every { waitingQueueRepository.getRank(scheduleId, uuid) } returns 11

        `when`("토큰 발급을 요청하면") {
            val result = issueQueueTokenService.issue(uuid, scheduleId)

            then("해당 스케줄 대기열에 등록한다") {
                result.scheduleId shouldBe scheduleId
                result.status shouldBe AdmissionStatus.WAITING.name
                result.rank shouldBe 11
                verify(exactly = 1) { waitingQueueRepository.add(scheduleId, uuid) }
            }
        }
    }
})
