package kr.hhplus.be.server.domains.queue.application.dto.response

import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus

data class QueueTokenResponse(
    val token: String? = null,
    val status: String,                // 현재 상태 ("ACTIVE" / "WAITING")
    val rank: Long? = null,            // (대기 중일 때) 대기 순서
    val remainingSeconds: Long? = null   // (활성 상태일 때) 잔여 초
) {

    companion object {
        fun active(token: String, remainingSeconds: Long): QueueTokenResponse {
            return QueueTokenResponse(
                token = token,
                status = AdmissionStatus.ACTIVE.name,
                remainingSeconds = remainingSeconds
            )
        }

        fun waiting(rank: Long?): QueueTokenResponse {
            return QueueTokenResponse(
                status = AdmissionStatus.WAITING.name,
                rank = rank
            )
        }
    }

}