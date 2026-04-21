package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository

class ExpireQueueTokenService(
    private val activeQueueRepository: ActiveQueueRepository,
) {

    fun execute() {
        activeQueueRepository.removeExpiredActive()
    }

}
