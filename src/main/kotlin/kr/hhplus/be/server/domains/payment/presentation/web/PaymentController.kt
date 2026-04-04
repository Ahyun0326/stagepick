package kr.hhplus.be.server.domains.payment.presentation.web

import kr.hhplus.be.server.common.response.ApiResponse
import kr.hhplus.be.server.domains.payment.application.dto.PaymentRequest
import kr.hhplus.be.server.domains.payment.application.dto.PaymentResponse
import kr.hhplus.be.server.domains.payment.application.dto.PendingPaymentInfoResponse
import kr.hhplus.be.server.domains.payment.application.facade.PaymentFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentFacade: PaymentFacade
) {

    @GetMapping("/{reservationId}")
    fun findPendingPaymentInfo(@PathVariable("reservationId") reservationId: Long): ApiResponse<PendingPaymentInfoResponse> {
        return ApiResponse.success(paymentFacade.findPendingPaymentInfo(reservationId))
    }

    @PostMapping
    fun processPayment(@RequestBody request: PaymentRequest): ApiResponse<PaymentResponse> {
        return ApiResponse.success(paymentFacade.processPayment(request))
    }
}