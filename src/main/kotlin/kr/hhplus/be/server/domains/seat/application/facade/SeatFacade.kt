package kr.hhplus.be.server.domains.seat.application.facade

import kr.hhplus.be.server.domains.seat.application.dto.SeatsInfo
import kr.hhplus.be.server.domains.seat.application.usecase.ExpireSeatHoldService
import kr.hhplus.be.server.domains.seat.application.usecase.GetSeatsInfoService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SeatFacade(
    private val getSeatsInfoService: GetSeatsInfoService,
    private val expireSeatHoldService: ExpireSeatHoldService
) {

    /**
     * 트랜잭션 경계 설정
     * - 실제 비즈니스 로직은 유스케이스에 위임
     * - 여기서는 트랜잭션 시작/종료만 담당
     */

    @Transactional(readOnly = true)
    fun getSeatsInfo(scheduleId: Long): SeatsInfo {
        return getSeatsInfoService.invoke(scheduleId)
    }

    @Transactional
    fun expireSeatHold() {
        expireSeatHoldService.processExpiredSeats()
    }

}