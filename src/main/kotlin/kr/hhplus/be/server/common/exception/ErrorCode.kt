package kr.hhplus.be.server.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: Int,
    val message: String
) {
    // 공통 에러
    BAD_REQUEST(HttpStatus.BAD_REQUEST, 1001, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 1002, "서버 내부 오류가 발생했습니다."),

    // 콘서트 도메인 에러
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, 2001, "요청하신 콘서트 정보를 찾을 수 없습니다."),

    // 스케줄 도메인 에러
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, 3001, "존재하지 않는 공연 회차입니다."),

    // 좌석 도메인 에러
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, 4001, "존재하지 않는 좌석 정보입니다."),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT, 4002, "이용 불가능한 좌석입니다."),

    // 포인트 도메인 에러
    NEGATIVE_POINT(HttpStatus.CONFLICT, 5001, "충전 금액은 0보다 커야 합니다."),
    INSUFFICIENT_POINT(HttpStatus.CONFLICT, 5002, "결제 포인트가 부족합니다."),
    POINT_NOT_FOUND(HttpStatus.NOT_FOUND, 5003, "포인트 정보를 찾을 수 없습니다."),

    // 예약 도메인 에러
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, 6001, "예약 정보를 찾을 수 없습니다."),
    RESERVATION_SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, 6002, "예약 ID에 해당하는 좌석을 찾을 수 없습니다."),
    RESERVATION_SEAT_EXPIRED(HttpStatus.NOT_FOUND, 6003, "배정된 좌석의 예약 유효 시간이 만료되었습니다."),

    // 결제 도메인 에러
    PAYMENT_INFO_NOT_FOUND(HttpStatus.NOT_FOUND, 7001, "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MATCH_EXCEPTION(HttpStatus.CONFLICT, 7002, "결제 요청 금액이 불일치합니다."),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, 7003, "이미 결제가 완료된 예약입니다.")

}