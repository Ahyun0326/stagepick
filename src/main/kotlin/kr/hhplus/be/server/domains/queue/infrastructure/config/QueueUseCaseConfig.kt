package kr.hhplus.be.server.domains.queue.infrastructure.config

import kr.hhplus.be.server.domains.queue.application.usecase.ExpireQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.GetQueueStatusService
import kr.hhplus.be.server.domains.queue.application.usecase.IssueQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.PromoteQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.ValidateQueueTokenService
import kr.hhplus.be.server.domains.queue.domain.policy.ThresholdAdmissionPolicy
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueUseCaseConfig(
    private val activeQueueRepository: ActiveQueueRepository,
    private val waitingQueueRepository: WaitingQueueRepository
) {

    @Bean
    fun issueQueueTokenService(): IssueQueueTokenService =
        IssueQueueTokenService(waitingQueueRepository, activeQueueRepository, ThresholdAdmissionPolicy(1000))

    @Bean
    fun getQueueStatusService(): GetQueueStatusService =
        GetQueueStatusService(activeQueueRepository, waitingQueueRepository)

    @Bean
    fun expireQueueTokenService(): ExpireQueueTokenService =
        ExpireQueueTokenService(activeQueueRepository)

    @Bean
    fun promoteQueueTokenService(): PromoteQueueTokenService =
        PromoteQueueTokenService(activeQueueRepository, waitingQueueRepository)

    @Bean
    fun validateQueueTokenService(): ValidateQueueTokenService =
        ValidateQueueTokenService(activeQueueRepository)
}
