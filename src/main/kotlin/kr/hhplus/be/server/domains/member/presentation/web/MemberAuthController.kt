package kr.hhplus.be.server.domains.member.presentation.web

import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import kr.hhplus.be.server.common.response.ApiResponse
import kr.hhplus.be.server.domains.member.application.MemberAuthService
import kr.hhplus.be.server.domains.member.application.dto.request.MemberSignInRequest
import kr.hhplus.be.server.domains.member.application.dto.request.MemberSignUpRequest
import kr.hhplus.be.server.domains.member.application.dto.response.MemberRefreshResponse
import kr.hhplus.be.server.domains.member.application.dto.response.MemberSignInResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/v1/auth")
class MemberAuthController(
    private val memberAuthService: MemberAuthService
) {

    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid request: MemberSignUpRequest): ApiResponse<Unit> {
        memberAuthService.signUp(request)
        return ApiResponse.success()
    }

    @PostMapping("/signin")
    fun signIn(
        @RequestBody @Valid request: MemberSignInRequest,
        response: HttpServletResponse,
    ): ApiResponse<MemberSignInResponse> {
        val (body, refreshToken) = memberAuthService.signIn(request)
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(refreshToken).toString())

        return ApiResponse.success(body)
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue("refreshToken") refreshToken: String,
        response: HttpServletResponse,
    ): ApiResponse<MemberRefreshResponse> {
        val (body, newRefreshToken) = memberAuthService.refresh(refreshToken)
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(newRefreshToken).toString())

        return ApiResponse.success(body)
    }

    private fun buildRefreshTokenCookie(refreshToken: String): ResponseCookie {
        return ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(true)
            .path("/api/v1/auth/refresh")
            .maxAge(Duration.ofDays(1))
            .sameSite("Strict")
            .build()
    }
}