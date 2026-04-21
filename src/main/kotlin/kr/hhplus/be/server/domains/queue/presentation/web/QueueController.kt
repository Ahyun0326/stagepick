package kr.hhplus.be.server.domains.queue.presentation.web

import kr.hhplus.be.server.common.jwt.JwtProvider
import kr.hhplus.be.server.common.response.ApiResponse
import kr.hhplus.be.server.domains.queue.application.dto.response.QueueTokenResponse
import kr.hhplus.be.server.domains.queue.application.facade.QueueFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue/tokens")
class QueueController(
    private val queueFacade: QueueFacade,
    private val jwtProvider: JwtProvider
) {

    @PostMapping
    fun issueToken(
        @RequestHeader("Authorization") authorization: String
    ): ApiResponse<QueueTokenResponse> {
        val uuid = extractUuid(authorization)
        return ApiResponse.success(queueFacade.issueToken(uuid))
    }

    @GetMapping("/me")
    fun getMyStatus(
        @RequestHeader("Authorization") authorization: String
    ): ApiResponse<QueueTokenResponse> {
        val uuid = extractUuid(authorization)
        return ApiResponse.success(queueFacade.getMyStatus(uuid))
    }

    private fun extractUuid(authorization: String): String {
        val token = authorization.removePrefix("Bearer ")
        return jwtProvider.getUUID(token)
    }
}
