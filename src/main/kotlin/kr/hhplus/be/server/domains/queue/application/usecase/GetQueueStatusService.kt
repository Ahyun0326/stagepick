package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.common.exception.QueueTokenNotFoundException
import kr.hhplus.be.server.domains.queue.application.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class GetQueueStatusService(
    private val activeQueueRepository: ActiveQueueRepository,
    private val waitingQueueRepository: WaitingQueueRepository
) {

    fun getStatus(uuid: String): QueueTokenResponse {
        activeQueueRepository.findActive(uuid)?.let { token ->
            return QueueTokenResponse.active(
                token = token,
                remainingSeconds = activeQueueRepository.getRemainingSeconds(uuid)
            )
        }

        if (waitingQueueRepository.isWaiting(uuid)) {
            return QueueTokenResponse.waiting(rank = waitingQueueRepository.getRank(uuid))
        }

        throw QueueTokenNotFoundException()
    }
}
