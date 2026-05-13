package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository

class PromoteQueueTokenService(
    private val activeQueueRepository: ActiveQueueRepository,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val capacity: Long,
    private val admissionRatePerTick: Long
) {

    fun execute() {
        val availableSlots = capacity - activeQueueRepository.countActive()
        val promoteCount = minOf(availableSlots, admissionRatePerTick)

        if (promoteCount > 0) {
            waitingQueueRepository.popWaiting(promoteCount)
                .forEach { activeQueueRepository.saveActive(it) }
        }
    }

}
