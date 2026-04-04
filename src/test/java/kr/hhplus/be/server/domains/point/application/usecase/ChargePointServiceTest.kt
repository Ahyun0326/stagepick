package kr.hhplus.be.server.domains.point.application.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kr.hhplus.be.server.common.exception.NegativePointException
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.validator.PointValidator
import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.repository.PointHistoryRepository
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import java.util.Optional

class ChargePointServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val pointRepository: PointRepository = mockk()
    val pointHistoryRepository: PointHistoryRepository = mockk()
    val pointValidator: PointValidator = mockk()

    val chargePointService = ChargePointService(
        pointRepository,
        pointHistoryRepository,
        pointValidator
    )

    given("0 이하의 포인트로") {
        val memberId = 1L
        val amount = 0
        val request = ChargePointRequest(amount)

        every { pointValidator.validateNegativePoint(amount) } throws NegativePointException()

        `when`("포인트를 충전하면") {
            then("NegativePointException이 발생한다") {
                shouldThrow<NegativePointException> {
                    chargePointService.invoke(memberId, request)
                }
            }
        }
    }

    given("저장된 포인트가 존재하는 memberId로") {
        val memberId = 1L
        val amount = 10000
        val point = Point(1L, memberId, 15000)

        val request = ChargePointRequest(amount)

        every { pointValidator.validateNegativePoint(amount) } just runs
        every { pointRepository.findPointByMemberId(memberId) } returns Optional.of(point)
        every { pointRepository.save(point) } returns point // 입력받은 객체를 그대로 반환하도록 설정
        every { pointHistoryRepository.save(any()) } returnsArgument 0

        `when`("포인트를 충전하면") {
            then("기존 포인트가 변경된다") {
                chargePointService.invoke(memberId, request)

                verify(exactly = 1) { pointRepository.save(point) }
                point.point shouldBe 25000
            }
        }
    }

    given("저장된 포인트가 존재하지 않는 memberId로") {
        val memberId = 1L
        val amount = 10000

        val request = ChargePointRequest(amount)

        every { pointValidator.validateNegativePoint(amount) } just runs
        every { pointRepository.findPointByMemberId(memberId) } returns Optional.empty<Point>()
        every { pointRepository.save(any()) } returnsArgument 0 // 입력받은 객체를 그대로 반환하도록 설정
        every { pointHistoryRepository.save(any()) } returnsArgument 0

        `when`("포인트를 충전하면") {
            then("새로운 포인트가 저장된다") {
                chargePointService.invoke(memberId, request)

                val pointSlot = slot<Point>()
                verify(exactly = 1) { pointRepository.save(capture(pointSlot)) }
                pointSlot.captured.point shouldBe amount
            }
        }
    }




})
