package kr.hhplus.be.server.domains.point.application.validator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import kr.hhplus.be.server.common.exception.InsufficientPointException
import kr.hhplus.be.server.common.exception.NegativePointException
import kr.hhplus.be.server.common.exception.PaymentAmountMatchException

class PointValidatorTest : FunSpec({
    val pointValidator = PointValidator()

    context("0 이하의 포인트 입력 검증") {
        test("양수면 성공한다") {
            pointValidator.validateNegativePoint(1)
        }

        test("0이면 NegativePointException이 발생한다") {
            shouldThrow<NegativePointException> {
                pointValidator.validateNegativePoint(0)
            }
        }

        test("음수면 NegativePointException이 발생한다") {
            shouldThrow<NegativePointException> {
                pointValidator.validateNegativePoint(-1)
            }
        }
    }

    context("포인트 부족 검증") {
        val point = 100000
        test("포인트가 결제 금액보다 많으면 성공한다") {
            val paymentAmount = 50000
            pointValidator.validateInsufficientPoint(point, paymentAmount)
        }

        test("포인트가 결제 금액과 같으면 성공한다") {
            val paymentAmount = 100000
            pointValidator.validateInsufficientPoint(point, paymentAmount)
        }

        test("포인트가 결제 금액보다 부족하면 InsufficientPointException이 발생한다") {
            val paymentAmount = 110000

            shouldThrow<InsufficientPointException> {
                pointValidator.validateInsufficientPoint(point, paymentAmount)
            }
        }
    }

    context("결제 금액과 포인트 일치 검증") {
        val paymentAmount = 100000

        test("결제 금액과 포인트가 같으면 성공한다") {
            val point = 100000
            pointValidator.validatePaymentAmountMatch(paymentAmount, point)

        }

        test("결제 금액과 포인트가 다르면 PaymentAmountMatchException이 발생한다") {
            val point = 200000

            shouldThrow<PaymentAmountMatchException> {
                pointValidator.validatePaymentAmountMatch(paymentAmount, point)
            }
        }
    }


})
