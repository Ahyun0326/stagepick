package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.domains.queue.application.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus
import kr.hhplus.be.server.domains.queue.domain.policy.AdmissionPolicy
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class IssueQueueTokenService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val activeQueueRepository: ActiveQueueRepository,
    private val policy: AdmissionPolicy
) {

    /**
     * 대기열 토큰 발급
     */
    fun issue(uuid: String) : QueueTokenResponse {

        // 이미 활성 상태인 유저인지 확인
        activeQueueRepository.findActive(uuid)?.let { token ->
            return QueueTokenResponse.active(
                token = token,
                remainingSeconds = activeQueueRepository.getRemainingSeconds(uuid)
            )
        }

        // 현재 대기열에 등록된 유저인지 확인
        if (waitingQueueRepository.isWaiting(uuid)) {
            return QueueTokenResponse.waiting(rank = waitingQueueRepository.getRank(uuid))
        }

        // 활성 인원 체크: 최대 1000명
        val activeCount = activeQueueRepository.countActive()

        return when (policy.judge(activeCount)) {
            // 활성 토큰 즉시 발급
            AdmissionStatus.ACTIVE -> {
                val token = activeQueueRepository.saveActive(uuid)
                val remainingSeconds = activeQueueRepository.getRemainingSeconds(uuid)

                QueueTokenResponse.active(token = token, remainingSeconds = remainingSeconds)
            }

            // 대기열 추가
            AdmissionStatus.WAITING -> {
                waitingQueueRepository.add(uuid)
                val rank = waitingQueueRepository.getRank(uuid)

                QueueTokenResponse.waiting(rank = rank)
            }
        }
    }
}