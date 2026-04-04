package kr.hhplus.be.server.common.exception

class SeatNotFoundException(
    val invalidSeatIds: List<Long>
) : CustomException(ErrorCode.SEAT_NOT_FOUND) {}