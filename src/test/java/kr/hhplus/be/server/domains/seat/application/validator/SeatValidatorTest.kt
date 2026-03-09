package kr.hhplus.be.server.domains.seat.application.validator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import kr.hhplus.be.server.common.exception.SeatNotFoundException
import kr.hhplus.be.server.common.exception.SeatUnavailableException

class SeatValidatorTest : FunSpec({

    val seatValidator = SeatValidator()

    context("좌석 존재 여부 검증") {
        val existingIds = listOf<Long>(1,2,3)

        test("요청한 좌석이 모두 존재하면 성공한다") {
            val requestIds = listOf<Long>(1,2)

            seatValidator.validateExistingSeats(existingIds, requestIds)
        }

        test("요청한 좌석에 존재하지 않는 좌석이 있으면 SeatNotFoundException이 발생한다") {
            val requestIds = listOf<Long>(1, 4)

            shouldThrow<SeatNotFoundException> {
                seatValidator.validateExistingSeats(existingIds, requestIds)
            }
        }
    }

    context("예약 가능한 좌석 검증") {
        test("예약할 수 없는 좌석이 없으면 성공한다") {
            val seatIds = emptyList<Long>()

            seatValidator.validateAvailableSeats(seatIds)
        }

        test("예약할 수 없는 좌석이 있으면 SeatUnavailableException이 발생한다") {
            val seatIds = listOf<Long>(1,2)

            shouldThrow<SeatUnavailableException> {
                seatValidator.validateAvailableSeats(seatIds)
            }
        }
    }

})
