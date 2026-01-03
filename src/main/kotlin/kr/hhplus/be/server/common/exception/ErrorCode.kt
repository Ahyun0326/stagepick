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

}