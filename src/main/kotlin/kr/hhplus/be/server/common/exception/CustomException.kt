package kr.hhplus.be.server.common.exception

import java.lang.RuntimeException

open class CustomException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message) {}