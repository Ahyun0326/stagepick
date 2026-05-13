package kr.hhplus.be.server.domains.queue.presentation.scheduler

import kr.hhplus.be.server.domains.queue.application.facade.QueueFacade
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class QueueScheduler(
    private val queueFacade: QueueFacade
) {

    /**
     * 설정된 주기마다 만료된 토큰 정리 & 대기자 승격
     */
    @Scheduled(fixedRateString = "\${queue.scheduler-interval-ms}")
    fun processQueue() {
        queueFacade.expireOverdueTokens()
        queueFacade.promoteWaitingTokens()
    }
}