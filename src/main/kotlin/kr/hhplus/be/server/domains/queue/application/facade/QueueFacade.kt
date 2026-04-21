package kr.hhplus.be.server.domains.queue.application.facade

import kr.hhplus.be.server.domains.queue.application.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domains.queue.application.usecase.ExpireQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.GetQueueStatusService
import kr.hhplus.be.server.domains.queue.application.usecase.IssueQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.PromoteQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.ValidateQueueTokenService
import org.springframework.stereotype.Component

@Component
class QueueFacade(
    private val issueQueueTokenService: IssueQueueTokenService,
    private val getQueueStatusService: GetQueueStatusService,
    private val expireQueueTokenService: ExpireQueueTokenService,
    private val promoteQueueTokenService: PromoteQueueTokenService,
    private val validateQueueTokenService: ValidateQueueTokenService
) {

    fun issueToken(uuid: String): QueueTokenResponse = issueQueueTokenService.issue(uuid)

    fun getMyStatus(uuid: String): QueueTokenResponse = getQueueStatusService.getStatus(uuid)

    fun expireOverdueTokens() = expireQueueTokenService.execute()

    fun promoteWaitingTokens() = promoteQueueTokenService.execute()

    fun validateToken(token: String) = validateQueueTokenService.validate(token)

}
