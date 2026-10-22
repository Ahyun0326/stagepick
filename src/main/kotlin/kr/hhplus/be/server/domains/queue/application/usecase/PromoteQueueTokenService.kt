package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository
import java.util.UUID

class PromoteQueueTokenService(
    private val activeQueueRepository: ActiveQueueRepository,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val capacity: Long,
    private val admissionRatePerTick: Long
) {

    fun execute() {
        waitingQueueRepository.findScheduleIds().forEach { scheduleId ->
            promoteWaitingTokens(scheduleId)
        }
    }

    private fun promoteWaitingTokens(scheduleId: Long) {
        // "남은 슬롯 계산 → 대기열에서 꺼내기 → 활성 등록"은 더 이상 이 UseCase에서 순차적으로
        // 호출하지 않는다 — 여러 인스턴스가 동시에 돌면 check-then-act 경합으로 정원을 넘길 수
        // 있었다(STEP4 선택 심화 ② 보완, RED 실측: 정원 5인데 활성 50명). 지금은 Redis Lua EVAL
        // 하나(`ActiveQueueRepository.promote`)로 원자화했다(ADR-7).
        //
        // Lua 안에서는 새 토큰(UUID)을 만들 수 없어, 이번 tick에서 최대로 승격될 수 있는 인원
        // (admissionRatePerTick)만큼 신규 활성 토큰 후보를 미리 만들어 전달한다. 실제로 승격되는
        // 인원이 이보다 적으면 남는 후보는 Lua 쪽에서 그냥 버려진다.
        val candidateTokens = (1..admissionRatePerTick).map { UUID.randomUUID().toString() }

        activeQueueRepository.promote(scheduleId, capacity, admissionRatePerTick, candidateTokens)
    }

}
