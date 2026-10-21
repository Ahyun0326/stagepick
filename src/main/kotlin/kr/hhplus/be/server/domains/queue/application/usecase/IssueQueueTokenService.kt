package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.domains.queue.application.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class IssueQueueTokenService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val activeQueueRepository: ActiveQueueRepository,
    private val capacity: Long
) {

    /**
     * 대기열 토큰 발급
     */
    fun issue(uuid: String, scheduleId: Long): QueueTokenResponse {

        // 이미 활성 상태인 유저인지 확인
        activeQueueRepository.findActive(scheduleId, uuid)?.let { token ->
            return QueueTokenResponse.active(
                scheduleId = scheduleId,
                token = token,
                remainingSeconds = activeQueueRepository.getRemainingSeconds(scheduleId, uuid)
            )
        }

        // 현재 대기열에 등록된 유저인지 확인
        if (waitingQueueRepository.isWaiting(scheduleId, uuid)) {
            waitingQueueRepository.touchHeartbeat(scheduleId, uuid)

            return QueueTokenResponse.waiting(
                scheduleId = scheduleId,
                rank = waitingQueueRepository.getRank(scheduleId, uuid)
            )
        }

        // 정원 확인과 발급을 원자적으로 수행 (countActive 후 saveActive를 따로 호출하던
        // check-then-act 경합을 막는다 — STEP4 선택 심화 ②)
        val admitted = activeQueueRepository.tryIssueActive(scheduleId, uuid, capacity)

        if (admitted) {
            val token = activeQueueRepository.findActive(scheduleId, uuid)
                ?: throw IllegalStateException("활성 처리된 토큰을 찾을 수 없습니다. scheduleId=$scheduleId")

            return QueueTokenResponse.active(
                scheduleId = scheduleId,
                token = token,
                remainingSeconds = activeQueueRepository.getRemainingSeconds(scheduleId, uuid)
            )
        }

        // 정원이 가득 차 대기열로 등록
        waitingQueueRepository.add(scheduleId, uuid)
        val rank = waitingQueueRepository.getRank(scheduleId, uuid)

        return QueueTokenResponse.waiting(scheduleId = scheduleId, rank = rank)
    }
}
