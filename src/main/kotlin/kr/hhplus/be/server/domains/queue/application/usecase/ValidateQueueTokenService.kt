package kr.hhplus.be.server.domains.queue.application.usecase

import kr.hhplus.be.server.common.exception.QueueTokenInvalidException
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository

class ValidateQueueTokenService(
    private val activeQueueRepository: ActiveQueueRepository
) {

    fun validate(token: String) {
        if (!activeQueueRepository.isValidToken(token)) {
            throw QueueTokenInvalidException()
        }
    }
}