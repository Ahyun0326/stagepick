@file:Suppress("PROPERTY_HIDES_JAVA_FIELD")

package kr.hhplus.be.server.common.advice

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.hhplus.be.server.common.exception.ConcertNotFoundException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.common.exception.SeatNotFoundException
import kr.hhplus.be.server.common.exception.SeatUnavailableException
import kr.hhplus.be.server.common.response.ApiErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    val logger = KotlinLogging.logger("Exception Logger")

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle Exception $e" }

        return ResponseEntity
            .status(ErrorCode.INTERNAL_SERVER_ERROR.status)
            .body(
                ApiErrorResponse.of(
                    code = ErrorCode.INTERNAL_SERVER_ERROR.code,
                    message = ErrorCode.INTERNAL_SERVER_ERROR.message
                )
            )
    }

    @ExceptionHandler(ConcertNotFoundException::class)
    fun handleConcertNotFoundException(e: ConcertNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle CustomNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(ScheduleNotFoundException::class)
    fun handleScheduleNotFoundException(e: ScheduleNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle ScheduleNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(SeatNotFoundException::class)
    fun handleSeatNotFoundException(e: SeatNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle SeatNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message, e.invalidSeatIds))
    }

    @ExceptionHandler(SeatUnavailableException::class)
    fun handleSeatUnavailableException(e: SeatUnavailableException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle SeatUnavailableException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message, e.invalidSeatIds))
    }

}