package kr.hhplus.be.server.domains.point.application.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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

    given("유효한 포인트로") {
        val memberId = 1L
        val amount = 10000
        // 조건부 UPDATE(point = point + amount)를 저장소에 위임한 뒤 결과를 다시 조회하는 흐름이므로,
        // 조회 결과는 이미 충전이 반영된 값(25000)으로 설정한다. 최초 충전·동시 경합 재시도는
        // PointJpaRepository.chargePoint 책임이라 이 유스케이스 테스트에서는 검증하지 않는다
        // (해당 책임은 PointConcurrencyIntegrationTest에서 실제 DB로 검증한다).
        val chargedPoint = Point(1L, memberId, 25000)

        val request = ChargePointRequest(amount)

        every { pointValidator.validateNegativePoint(amount) } just runs
        every { pointRepository.chargePoint(memberId, amount) } returns 1
        every { pointRepository.findPointByMemberId(memberId) } returns Optional.of(chargedPoint)
        every { pointHistoryRepository.save(any()) } returnsArgument 0

        `when`("포인트를 충전하면") {
            then("원자적 조건부 UPDATE로 충전을 위임하고, save()는 직접 호출하지 않는다") {
                chargePointService.invoke(memberId, request)

                verify(exactly = 1) { pointRepository.chargePoint(memberId, amount) }
                verify(exactly = 0) { pointRepository.save(any()) }
            }
        }
    }
})
