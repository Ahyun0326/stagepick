package kr.hhplus.be.server.common.response

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiErrorResponse(
    val success: Boolean = false,
    val code: Int,
    val message: String,
    @param:JsonInclude(JsonInclude.Include.NON_NULL)
    val data: Any? = null,
) {
    companion object {
        fun of(code: Int, message: String): ApiErrorResponse {
            return ApiErrorResponse(
                code = code,
                message = message
            )
        }

        fun of(code: Int, message: String, data: Any): ApiErrorResponse {
            return ApiErrorResponse(
                code = code,
                message = message,
                data = data
            )
        }
    }
}