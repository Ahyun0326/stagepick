@file:Suppress("PROPERTY_HIDES_JAVA_FIELD")

package kr.hhplus.be.server.common.advice

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.hhplus.be.server.common.exception.ConcertNotFoundException
import kr.hhplus.be.server.common.exception.CustomException
import kr.hhplus.be.server.common.exception.DuplicatePaymentException
import kr.hhplus.be.server.common.exception.ErrorCode
import kr.hhplus.be.server.common.exception.InsufficientPointException
import kr.hhplus.be.server.common.exception.MemberNotFoundException
import kr.hhplus.be.server.common.exception.NegativePointException
import kr.hhplus.be.server.common.exception.PasswordMismatchException
import kr.hhplus.be.server.common.exception.PaymentAmountMatchException
import kr.hhplus.be.server.common.exception.PaymentInfoNotFoundException
import kr.hhplus.be.server.common.exception.PointNotFoundException
import kr.hhplus.be.server.common.exception.ReservationNotFoundException
import kr.hhplus.be.server.common.exception.ReservationSeatExpiredException
import kr.hhplus.be.server.common.exception.ReservationSeatNotFoundException
import kr.hhplus.be.server.common.exception.ScheduleNotFoundException
import kr.hhplus.be.server.common.exception.SeatNotFoundException
import kr.hhplus.be.server.common.exception.SeatUnavailableException
import kr.hhplus.be.server.common.response.ApiErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    val logger = KotlinLogging.logger("Exception Logger")

    @ExceptionHandler(CustomException::class)
    fun handleCustomException(e: CustomException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle CustomException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

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

    override fun handleMethodArgumentNotValid(
        e: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any> {
        logger.error(e) { "Handle methodArgumentNotValidException $e" }

        val errorMap = e.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "Invalid Argument Value")
        }

        return ResponseEntity
            .status(ErrorCode.INVALID_INPUT_VALUE.status)
            .body(ApiErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE.code, ErrorCode.INVALID_INPUT_VALUE.message, errorMap))
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

    @ExceptionHandler(NegativePointException::class)
    fun handleNegativePointException(e: NegativePointException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle NegativePointException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(InsufficientPointException::class)
    fun handleInsufficientPointException(e: InsufficientPointException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle insufficientPointException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(ReservationNotFoundException::class)
    fun handleReservationNotFoundException(e: ReservationNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle ReservationNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(ReservationSeatNotFoundException::class)
    fun handleReservationSeatNotFoundException(e: ReservationSeatNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle ReservationSeatNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(ReservationSeatExpiredException::class)
    fun handleReservationSeatExpiredException(e: ReservationSeatExpiredException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle reservationExpiredException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(PaymentInfoNotFoundException::class)
    fun handlePaymentInfoNotFoundException(e: PaymentInfoNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle PaymentInfoNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(PaymentAmountMatchException::class)
    fun handlePaymentAmountMatchException(e: PaymentAmountMatchException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle PaymentAmountMatchException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(DuplicatePaymentException::class)
    fun handleDuplicatePaymentException(e: DuplicatePaymentException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle DuplicatePaymentException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(PointNotFoundException::class)
    fun handlePointNotFoundException(e: PointNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle PointNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(MemberNotFoundException::class)
    fun handleMemberNotFoundException(e: MemberNotFoundException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle MemberNotFoundException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(PasswordMismatchException::class)
    fun handlePasswordMismatchException(e: PasswordMismatchException): ResponseEntity<ApiErrorResponse> {
        logger.error(e) { "Handle PasswordMismatchException $e" }

        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiErrorResponse.of(e.errorCode.code, e.errorCode.message))
    }



}
