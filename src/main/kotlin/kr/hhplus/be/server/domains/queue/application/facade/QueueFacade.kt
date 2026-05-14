package kr.hhplus.be.server.domains.queue.application.facade

import kr.hhplus.be.server.domains.queue.application.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domains.queue.application.usecase.ExpireQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.ExpireWaitingQueueTokenService
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
    private val expireWaitingQueueTokenService: ExpireWaitingQueueTokenService,
    private val promoteQueueTokenService: PromoteQueueTokenService,
    private val validateQueueTokenService: ValidateQueueTokenService
) {

    fun issueToken(uuid: String, scheduleId: Long): QueueTokenResponse =
        issueQueueTokenService.issue(uuid, scheduleId)

    fun getMyStatus(uuid: String, scheduleId: Long): QueueTokenResponse =
        getQueueStatusService.getStatus(uuid, scheduleId)

    fun expireOverdueTokens() {
        expireQueueTokenService.execute()
        expireWaitingQueueTokenService.execute()
    }

    fun promoteWaitingTokens() = promoteQueueTokenService.execute()

    fun validateToken(uuid: String, scheduleId: Long, token: String) =
        validateQueueTokenService.validate(uuid, scheduleId, token)

}
