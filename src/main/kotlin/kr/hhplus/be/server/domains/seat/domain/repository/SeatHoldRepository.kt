package kr.hhplus.be.server.domains.seat.domain.repository

import kr.hhplus.be.server.domains.seat.domain.model.Seat

interface SeatHoldRepository {
    fun hold(seats: List<Seat>)
    fun getHeldStatus(seatIds: List<Long>): List<Boolean>
    fun getExpiredSeats(): List<Long>

    /** 모든 좌석의 홀드가 아직 만료 전인지 (만료 큐의 만료 시각 기준) */
    fun isAllHoldActive(seatIds: List<Long>): Boolean
    /** 결제 확정된 좌석의 홀드 키와 만료 큐 항목을 함께(원자적으로) 지운다 */
    fun removeSeats(seatIds: List<Long>)

    /**
     * 아직 만료 상태(만료 시각 ≤ 지금)인 홀드만 지운다
     * - 해제 커밋과 정리 사이에 다른 사용자가 같은 좌석을 다시 홀드했다면(만료 시각이 미래) 남긴다
     */
    fun removeExpiredSeats(seatIds: List<Long>)
}