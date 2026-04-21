package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class PromoteQueueTokenService(
    private val activeQueueRepository: ActiveQueueRepository,
    private val waitingQueueRepository: WaitingQueueRepository
) {

    fun execute() {
        val remainingCount = 1000 - activeQueueRepository.countActive()

        if (remainingCount > 0) {
            waitingQueueRepository.popWaiting(remainingCount)
                .forEach { activeQueueRepository.saveActive(it) }
        }
    }

}
