package kr.hhplus.be.server.common.response

import org.springframework.http.HttpStatus

class ApiCommonResponse(
    val success: Boolean = true,
    val code: Int = HttpStatus.OK.value(),
    val message: String
) {
    companion object {
        fun success(message: String = "요청에 성공하였습니다."): ApiCommonResponse {
            return ApiCommonResponse(
                message = message
            )
        }
    }
}