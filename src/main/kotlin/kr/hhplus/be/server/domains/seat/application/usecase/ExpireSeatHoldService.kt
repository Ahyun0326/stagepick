package kr.hhplus.be.server.domains.seat.application.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.hhplus.be.server.domains.common.transaction.AfterCommitExecutor
import kr.hhplus.be.server.domains.seat.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domains.seat.domain.repository.SeatRepository

class ExpireSeatHoldService(
    private val seatRepository: SeatRepository,
    private val seatHoldRepository: SeatHoldRepository,
    private val afterCommitExecutor: AfterCommitExecutor
) {
    private val logger = KotlinLogging.logger {}

    fun processExpiredSeats() {
        val expiredSeatIds = seatHoldRepository.getExpiredSeats()

        if (expiredSeatIds.isNotEmpty()) {
            // HOLD 인 좌석만 해제 → 결제가 먼저 끝난 좌석(RESERVED)은 그대로 둔다
            val released = seatRepository.releaseHeldSeats(expiredSeatIds)
            if (released != expiredSeatIds.size) {
                // 결제와 경합했거나 커밋 후 정리가 실패해 남은 항목 — 좌석은 건드리지 않고 Redis 만 정리
                logger.info { "만료 대상 ${expiredSeatIds.size}건 중 ${released}건 해제 (나머지는 이미 확정·해제됨)" }
            }

            // 해제가 커밋된 뒤, 그때까지도 만료 상태인 홀드만 정리 (그 사이 다시 잡힌 홀드는 보존)
            afterCommitExecutor.execute { seatHoldRepository.removeExpiredSeats(expiredSeatIds) }
        }
    }
}
