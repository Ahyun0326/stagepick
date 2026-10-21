package kr.hhplus.be.server.domains.point.domain.repository

import kr.hhplus.be.server.domains.point.domain.model.Point
import java.util.Optional

interface PointRepository {
    fun findPointByMemberId(memberId: Long): Optional<Point>
    fun save(point: Point): Point

    /**
     * 원자적 충전(UPSERT): 해당 회원의 포인트 행이 있으면 `point = point + amount`로 더하고,
     * 없으면 `amount`로 새 행을 만든다 (INSERT ... ON DUPLICATE KEY UPDATE, 단일 원자적 SQL문).
     * 동시성 방어선: 두 번의 동시 충전끼리의 lost update, 그리고 "최초 충전" 자체가 동시에
     * 겹칠 때 중복 행이 생기는 경합을 모두 DB 한 문장으로 막는다.
     *
     * 반환값은 MySQL의 ON DUPLICATE KEY UPDATE 특성상 신규 삽입이면 1, 기존 행 갱신이면 2다
     * (실측 확인) — "0건이면 실패"로 판단하는 [usePoint]와 달리 성공 여부 판단에 쓰지 않는다.
     */
    fun chargePoint(memberId: Long, amount: Int): Int

    /**
     * 조건부 UPDATE(원자적 차감): `point = point - amount WHERE member_id = :memberId AND point >= amount`.
     * 조건을 만족하는 행이 없으면(잔액 부족 또는 회원 포인트 없음) 0을 반환한다.
     * 동시성 방어선: 같은 회원의 서로 다른 예약 결제가 동시에 들어와도 잔액을 초과해 차감하지 못한다.
     */
    fun usePoint(memberId: Long, amount: Int): Int
}