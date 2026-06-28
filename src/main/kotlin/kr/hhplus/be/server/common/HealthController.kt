package kr.hhplus.be.server.common

import kr.hhplus.be.server.common.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): ApiResponse<Unit> {
        return ApiResponse.success()
    }
}
