package kr.hhplus.be.server.domains.seat.domain

enum class SeatStatus(
    val value: String
) {
    AVAILABLE("예약 가능"),
    HOLD("에약 보류"),
    RESERVED("에약 완료"),
    CANCELED("예약 취소")
}