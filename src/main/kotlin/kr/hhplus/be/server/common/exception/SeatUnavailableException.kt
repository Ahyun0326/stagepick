package kr.hhplus.be.server.common.exception

class SeatUnavailableException(
    val invalidSeatIds: List<Long>
) : CustomException(ErrorCode.SEAT_UNAVAILABLE) {}