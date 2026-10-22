package kr.hhplus.be.server.domains.queue.application.usecase

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class PromoteQueueTokenServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val activeQueueRepository: ActiveQueueRepository = mockk()
    val waitingQueueRepository: WaitingQueueRepository = mockk()

    // "남은 슬롯 계산 → 꺼내기 → 등록"은 더 이상 이 UseCase가 직접 순서대로 호출하지 않는다 —
    // 멀티 인스턴스 경합(STEP4 선택 심화 ② 보완, ADR-7)을 막기 위해 ActiveQueueRepository.promote
    // 하나의 Redis Lua EVAL로 옮겼다. 그래서 이 테스트는 "그 호출에 제대로 위임하는지"만 검증한다 —
    // 실제 원자성(정원 초과 방지)은 Redis 없이는 검증할 수 없어
    // QueueTokenLifecycleConcurrencyIntegrationTest(실제 Redis)에서 검증한다.

    given("승격 대상 스케줄이 여러 개 있을 때") {
        val promoteQueueTokenService = PromoteQueueTokenService(
            activeQueueRepository,
            waitingQueueRepository,
            capacity = 1000,
            admissionRatePerTick = 30
        )

        every { waitingQueueRepository.findScheduleIds() } returns listOf(1L, 2L)
        every { activeQueueRepository.promote(any(), any(), any(), any()) } returns emptyList()

        `when`("대기자를 승격하면") {
            promoteQueueTokenService.execute()

            then("스케줄마다 활성 저장소의 원자적 승격 연산을 정원·입장 허용 수와 함께 한 번씩 호출한다") {
                verify(exactly = 1) { activeQueueRepository.promote(1L, 1000, 30, any()) }
                verify(exactly = 1) { activeQueueRepository.promote(2L, 1000, 30, any()) }
            }
        }
    }

    given("승격 대상 스케줄이 없을 때") {
        val promoteQueueTokenService = PromoteQueueTokenService(
            activeQueueRepository,
            waitingQueueRepository,
            capacity = 1000,
            admissionRatePerTick = 30
        )

        every { waitingQueueRepository.findScheduleIds() } returns emptyList()

        `when`("대기자를 승격하면") {
            promoteQueueTokenService.execute()

            then("활성 저장소의 승격 연산을 호출하지 않는다") {
                verify(exactly = 0) { activeQueueRepository.promote(any(), any(), any(), any()) }
            }
        }
    }

    given("tick당 입장 허용 수(admissionRatePerTick)가 주어졌을 때") {
        val promoteQueueTokenService = PromoteQueueTokenService(
            activeQueueRepository,
            waitingQueueRepository,
            capacity = 1000,
            admissionRatePerTick = 5
        )
        val candidateTokens = slot<List<String>>()

        every { waitingQueueRepository.findScheduleIds() } returns listOf(1L)
        every { activeQueueRepository.promote(1L, 1000, 5, capture(candidateTokens)) } returns emptyList()

        `when`("대기자를 승격하면") {
            promoteQueueTokenService.execute()

            then("Lua가 만들 수 없는 신규 활성 토큰 후보를 admissionRatePerTick개만큼 미리 만들어 전달한다") {
                candidateTokens.captured.size shouldBe 5
                candidateTokens.captured.toSet().size shouldBe 5 // 서로 다른 UUID (중복 없음)
            }
        }
    }
})
