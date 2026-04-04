package kr.hhplus.be.server.domains.payment.application.facade

import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.dto.PaymentResponse
import kr.hhplus.be.server.domains.payment.application.dto.PendingPaymentInfoResponse
import kr.hhplus.be.server.domains.payment.application.usecase.FindPendingPaymentInfoService
import kr.hhplus.be.server.domains.payment.application.usecase.ProcessPaymentService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFacade(
    private val findPendingPaymentInfoService: FindPendingPaymentInfoService,
    private val processPaymentService: ProcessPaymentService
) {

    @Transactional(readOnly = true)
    fun findPendingPaymentInfo(reservationId: Long): PendingPaymentInfoResponse {
        return findPendingPaymentInfoService.invoke(reservationId)
    }

    @Transactional
    fun processPayment(request: PaymentRequest): PaymentResponse {
        return processPaymentService.process(request)
    }
}