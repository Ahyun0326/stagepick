package kr.hhplus.be.server.domains.point.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface SpringPointJpa : JpaRepository<PointEntity, Long> {
    fun findByMemberId(memberId: Long): Optional<PointEntity>

    // 충전은 "행이 있으면 더하고, 없으면 새로 만든다"를 네이티브 UPSERT(INSERT ... ON DUPLICATE KEY UPDATE)
    // 한 문장으로 원자화한다. JPA save()로 insert를 시도하고 UNIQUE(member_id) 위반을 잡아 같은
    // 트랜잭션에서 조건부 UPDATE로 재시도하는 방식은 실제로 재현해보니 위험했다: Hibernate가 제약
    // 위반(flush 실패) 이후 세션을 더 이상 정상적으로 쓸 수 없는 상태로 만들어, 재시도한 UPDATE가
    // 조용히 롤백되는 lost update가 관찰됐다(docs/report/step4-concurrency-report.md 참고).
    // DB가 insert/update를 한 문장에서 처리하는 UPSERT로 바꿔 이 문제를 근본적으로 없앤다.
    //
    // 반환값 주의(실측): MySQL의 "INSERT ... ON DUPLICATE KEY UPDATE"는 영향받은 행 수를
    // 신규 삽입이면 1, 기존 행을 실제로 바꾼 업데이트면 2로 보고한다(MySQL 공식 동작).
    // 그래서 이 메서드의 반환값은 "성공/실패(0건)"를 구분하는 usePoint와 달리 1 또는 2가 모두
    // 정상이며, 호출부(ChargePointService)도 반환값을 성공 여부 판단에 쓰지 않는다.
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            insert into point (member_id, point, created_at, updated_at)
            values (:memberId, :amount, now(6), now(6))
            on duplicate key update point = point + :amount, updated_at = now(6)
        """,
        nativeQuery = true
    )
    fun chargePoint(@Param("memberId") memberId: Long, @Param("amount") amount: Int): Int

    // 조건부 UPDATE: 잔액이 충분할 때만 차감된다 (같은 회원의 동시 결제 lost update 방지, 최후 방어선)
    // updated_at도 함께 갱신한다 — JPQL bulk update는 BaseEntity의 @LastModifiedDate(AuditingEntityListener)를
    // 거치지 않아(영속성 컨텍스트를 건드리지 않고 DB에 직접 UPDATE를 날림) 자동으로 갱신되지 않으므로,
    // chargePoint와 동일하게 수동으로 명시해 비대칭을 없앤다.
    //
    // JPQL `CURRENT_TIMESTAMP`로 먼저 시도했으나, 실제로 돌려보니 MySQL의 `current_timestamp`는
    // 정밀도 인자가 없으면 초 단위로만 값을 내려(마이크로초가 .000000으로 잘림) chargePoint의 `now(6)`
    // (마이크로초까지)와 정밀도가 달랐다 — 같은 초 안에서 충전 뒤 바로 차감하면 차감 쪽 updated_at이
    // 오히려 더 "이전"처럼 보이는 문제를 통합 테스트로 실측했다. 그래서 charge와 동일하게 네이티브
    // `now(6)`을 쓰는 네이티브 쿼리로 바꿔 정밀도까지 맞췄다.
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            update point set point = point - :amount, updated_at = now(6)
            where member_id = :memberId and point >= :amount
        """,
        nativeQuery = true
    )
    fun usePoint(@Param("memberId") memberId: Long, @Param("amount") amount: Int): Int
}
