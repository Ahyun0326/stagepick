package kr.hhplus.be.server.domains.point.application.usecase

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kr.hhplus.be.server.domains.point.domain.model.Point
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import java.util.Optional

class FindPointServiceTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val pointRepository: PointRepository = mockk()
    val findPointService = FindPointService(pointRepository)

    given("회원 ID로 충전된 포인트가 있을 때") {
        val memberId = 1L
        val point = Point(1L, memberId, 10000)

        every { pointRepository.findPointByMemberId(memberId) } returns Optional.of(point)

        `when`("포인트를 조회하면") {
            val result = findPointService.invoke(memberId)

            then("기존 포인트가 반환된다") {
                result.point shouldBe point.point
            }
        }
    }

    given("회원 ID로 충전된 포인트가 없을 때") {
        val memberId = 1L

        every { pointRepository.findPointByMemberId(memberId) } returns Optional.empty()

        `when`("포인트를 조회하면") {
            val result = findPointService.invoke(memberId)

            then("빈 포인트가 반환된다") {
                result.point shouldBe 0
            }
        }
    }


})
