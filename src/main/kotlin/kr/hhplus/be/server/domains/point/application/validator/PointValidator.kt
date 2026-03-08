package kr.hhplus.be.server.domains.point.application.validator

import kr.hhplus.be.server.common.exception.InsufficientPointException
import kr.hhplus.be.server.common.exception.NegativePointException
import kr.hhplus.be.server.common.exception.PaymentAmountMatchException
import org.springframework.stereotype.Component

@Component
class PointValidator {
    fun validateNegativePoint(amount: Int) {
        if (amount <= 0) {
            throw NegativePointException()
        }
    }

    fun validateInsufficientPoint(currentPoint: Int, amount: Int) {
        if (currentPoint < amount) {
            throw InsufficientPointException()
        }
    }

    fun validatePaymentAmountMatch(paymentAmount: Int, requestPoint: Int) {
        if (paymentAmount != requestPoint) {
            throw PaymentAmountMatchException()
        }
    }

}