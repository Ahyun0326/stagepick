package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.common.exception.QueueTokenNotFoundException
import kr.hhplus.be.server.domains.queue.application.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class GetQueueStatusService(
    private val activeQueueRepository: ActiveQueueRepository,
    private val waitingQueueRepository: WaitingQueueRepository
) {

    fun getStatus(uuid: String, scheduleId: Long): QueueTokenResponse {
        activeQueueRepository.findActive(scheduleId, uuid)?.let { token ->
            return QueueTokenResponse.active(
                scheduleId = scheduleId,
                token = token,
                remainingSeconds = activeQueueRepository.getRemainingSeconds(scheduleId, uuid)
            )
        }

        if (waitingQueueRepository.isWaiting(scheduleId, uuid)) {
            waitingQueueRepository.touchHeartbeat(scheduleId, uuid)
            return QueueTokenResponse.waiting(
                scheduleId = scheduleId,
                rank = waitingQueueRepository.getRank(scheduleId, uuid)
            )
        }

        throw QueueTokenNotFoundException()
    }
}
