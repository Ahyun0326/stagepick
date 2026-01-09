package kr.hhplus.be.server.domains.point.application.validator

import kr.hhplus.be.server.common.exception.NegativePointException
import org.springframework.stereotype.Component

@Component
class PointValidator {
    fun validateNegativePoint(amount: Int) {
        if (amount <= 0) {
            throw NegativePointException()
        }
    }

}