package kr.hhplus.be.server.domains.point.presentation.web

import kr.hhplus.be.server.common.response.ApiCommonResponse
import kr.hhplus.be.server.common.response.ApiResponse
import kr.hhplus.be.server.domains.point.application.PointService
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.dto.response.PointResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("api/v1/points")
class PointController(
    private val pointService: PointService,
) {
    @PostMapping
    fun chargePoint(@RequestBody request: ChargePointRequest): ApiCommonResponse {
        pointService.chargePoint(request)
        return ApiCommonResponse.success()
    }

    @GetMapping
    fun findPoint(): ApiResponse<PointResponse> {
        return ApiResponse.success(pointService.findPoint())
    }
}