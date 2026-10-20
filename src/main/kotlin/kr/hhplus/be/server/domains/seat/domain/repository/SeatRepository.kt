package kr.hhplus.be.server.domains.seat.domain.repository

import kr.hhplus.be.server.domains.seat.domain.model.Seat

interface SeatRepository {
    fun findSeatsByScheduleId(scheduleId: Long): List<Seat>
    fun findSeats(scheduleId: Long, seatIds: List<Long>): List<Seat>
    fun saveAll(seats: List<Seat>)
    /** AVAILABLE 인 좌석만 해당 예약의 HOLD 로 바꾼다 (이미 잡힌 좌석은 제외). 변경된 행 수 반환 */
    fun holdAvailableSeats(seatIds: List<Long>, reservationId: Long): Int

    /** HOLD 상태인 좌석만 AVAILABLE 로 해제한다 (이미 RESERVED 인 좌석은 건드리지 않음). 변경된 행 수 반환 */
    fun releaseHeldSeats(seatIds: List<Long>): Int
    fun findSeatsByReservationId(reservationId: Long): List<Seat>
    /** 해당 예약이 HOLD 중인 좌석만 RESERVED 로 확정한다 (이미 풀렸거나 다른 예약의 좌석은 제외). 변경된 행 수 반환 */
    fun confirmHeldSeats(seatIds: List<Long>, reservationId: Long): Int
}