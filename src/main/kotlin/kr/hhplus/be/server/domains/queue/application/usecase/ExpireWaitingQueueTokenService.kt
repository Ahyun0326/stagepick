package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class ExpireWaitingQueueTokenService(
    private val waitingQueueRepository: WaitingQueueRepository
) {

    fun execute() {
        waitingQueueRepository.findScheduleIds()
            .forEach { waitingQueueRepository.removeInactiveWaiting(it) }
    }
}
