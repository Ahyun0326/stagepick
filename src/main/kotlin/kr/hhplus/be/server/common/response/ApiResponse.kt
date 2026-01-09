package kr.hhplus.be.server.common.response

import org.springframework.http.HttpStatus

class ApiResponse<T>(
    val success: Boolean = true,
    val code: Int = HttpStatus.OK.value(),
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T, message: String = "요청에 성공하였습니다."): ApiResponse<T> {
            return ApiResponse(
                message = message,
                data = data
            )
        }
    }
}