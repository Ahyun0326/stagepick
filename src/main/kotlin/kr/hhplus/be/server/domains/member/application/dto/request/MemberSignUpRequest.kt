package kr.hhplus.be.server.domains.member.application.dto.request

import jakarta.validation.constraints.NotBlank

data class MemberSignUpRequest(
    @field:NotBlank(message = "로그인 아이디는 필수입니다.")
    val loginId: String,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String,

    @field:NotBlank(message = "이름은 필수입니다.")
    val username: String
) {}