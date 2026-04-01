package kr.hhplus.be.server.domains.payment.application.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.common.exception.PaymentInfoNotFoundException
import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.common.exception.ReservationSeatNotFoundException
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import kr.hhplus.be.server.domains.reservation.domain.repository.dto.ReservationPaymentDetailQueryDto
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository
import java.time.LocalDateTime

class FindPendingPaymentInfoServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val reservationRepository: ReservationRepository = mockk()
    val seatRepository: SeatRepository = mockk()

    val findPendingPaymentInfoService = FindPendingPaymentInfoService(reservationRepository, seatRepository)

    given("존재하지 않는 예약 ID로") {
        val reservationId = 1L

        every { reservationRepository.findById(reservationId) } returns null

        `when`("결제 정보를 조회하면") {
            then("ReservationNotFoundException이 발생한다") {
                shouldThrow<ReservationNotFoundException> {
                    findPendingPaymentInfoService.invoke(reservationId)
                }
            }
        }
    }

    given("예약 정보에 해당하는 좌석이 없는 경우") {
        val reservationId = 1L
        val reservation = Reservation("test", 1L).apply { assignId(reservationId) }

        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns emptyList()

        `when`("결제 정보를 조회하면") {
            then("ReservationSeatNotFoundException이 발생한다") {
                shouldThrow<ReservationSeatNotFoundException> {
                    findPendingPaymentInfoService.invoke(reservationId)
                }
            }
        }
    }

    given("예약에 해당하는 결제 정보가 없는 경우") {
        val reservationId = 1L
        val reservation = Reservation("test", 1L).apply { assignId(reservationId) }

        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(any()) } returns listOf(mockk())
        every { reservationRepository.getWithDetailsById(reservationId) } returns null

        `when`("결제 정보를 조회하면") {
            then("PaymentInfoNotFoundException이 발생한다") {
                shouldThrow<PaymentInfoNotFoundException> {
                    findPendingPaymentInfoService.invoke(reservationId)
                }
            }
        }
    }

    given("정상적인 예약 ID로") {
        val reservationId = 1L
        val reservation = Reservation("test", 1L).apply { assignId(reservationId) }

        val seats = (1L..2L).map { id ->
            Seat(id, 1L, "A$id", SeatStatus.HOLD.name, 5000)
        }

        val dto = ReservationPaymentDetailQueryDto(
            reservationId = reservationId,
            title = "concert1",
            location = "location1",
            viewingTime = 2,
            datetime = LocalDateTime.of(2026, 1, 1, 1, 1, 1),
            seatNumbers = seats.map { it.number },
            price = 10000
        )

        every { reservationRepository.findById(reservationId) } returns reservation
        every { seatRepository.findSeatsByReservationId(reservationId) } returns seats
        every { reservationRepository.getWithDetailsById(reservationId) } returns dto

        `when`("결제 정보를 조회하면") {
            val result = findPendingPaymentInfoService.invoke(reservationId)

            then("결제 대기 정보가 반환된다") {
                result.reservationId shouldBe reservationId
                result.title shouldBe dto.title
                result.seatNumbers shouldBe dto.seatNumbers
                result.price shouldBe dto.price
            }
        }
    }
})