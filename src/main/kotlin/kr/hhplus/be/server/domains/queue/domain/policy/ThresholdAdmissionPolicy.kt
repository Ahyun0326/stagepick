package kr.hhplus.be.server.domains.queue.domain.policy

import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus

class ThresholdAdmissionPolicy(
    private val threshold: Long
): AdmissionPolicy {
    override fun judge(activeCount: Long): AdmissionStatus {
        return if (activeCount < threshold) AdmissionStatus.ACTIVE
            else AdmissionStatus.WAITING
    }
}