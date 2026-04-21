package kr.hhplus.be.server.domains.queue.domain.policy

import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus

interface AdmissionPolicy {
    fun judge(activeCount: Long): AdmissionStatus
}