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

        val activeCount = activeQueueRepository.countActive(scheduleId)

        return when (policy.judge(activeCount)) {
            // 활성 토큰 즉시 발급
            AdmissionStatus.ACTIVE -> {
                val token = activeQueueRepository.saveActive(scheduleId, uuid)
                val remainingSeconds = activeQueueRepository.getRemainingSeconds(scheduleId, uuid)

                QueueTokenResponse.active(
                    scheduleId = scheduleId,
                    token = token,
                    remainingSeconds = remainingSeconds
                )
            }

            // 대기열 추가
            AdmissionStatus.WAITING -> {
                waitingQueueRepository.add(scheduleId, uuid)
                val rank = waitingQueueRepository.getRank(scheduleId, uuid)

                QueueTokenResponse.waiting(scheduleId = scheduleId, rank = rank)
            }
        }
    }
}
