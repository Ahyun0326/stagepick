package kr.hhplus.be.server.domains.point.infrastructure.persistence

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.point.domain.repository.PointRepository
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate

/**
 * 포인트 잔액 변경은 항상 `chargePoint`(UPSERT)/`usePoint`(조건부 UPDATE)라는 저장소의 원자적
 * 연산으로만 이뤄진다(STEP4 선택 심화 ②). 동시성 통합 테스트(`PointConcurrencyIntegrationTest`)는
 * 경합 시나리오만 다루므로, 단일 스레드에서의 기본 동작(신규 생성/기존 증가/정상 차감/잔액 부족)은
 * 여기서 별도로 검증한다 — 리뷰(`docs/agent/reviews/2026-10-21-concurrency-review.md`) LOW 항목.
 */
@DisplayName("[통합] 포인트 저장소 원자적 충전/차감")
class PointRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture
    @Autowired private lateinit var pointRepository: PointRepository
    @Autowired private lateinit var springPointJpa: SpringPointJpa
    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `포인트 행이 없는 회원을 충전하면 새 행이 1개 생성된다`() {
        // given - 포인트 행이 전혀 없는 신규 회원
        val member = fixture.createMember()

        // when
        val affected = inTx { pointRepository.chargePoint(member.id!!, CHARGE_AMOUNT) }

        // then
        affected shouldBe 1
        val entity = springPointJpa.findByMemberId(member.id!!).get()
        entity.point shouldBe CHARGE_AMOUNT
        entity.updatedAt.shouldNotBeNull()
    }

    @Test
    fun `포인트 행이 있는 회원을 충전하면 기존 값에 더해지고 행이 늘어나지 않는다`() {
        // given - 이미 포인트 행이 있는 회원
        val member = fixture.createMember()
        fixture.chargePoint(member.id!!, INITIAL_BALANCE)

        // when
        val affected = inTx { pointRepository.chargePoint(member.id!!, CHARGE_AMOUNT) }

        // then - 값은 더해지고(UPSERT의 UPDATE 분기), 같은 member_id 행은 여전히 1개뿐이다(중복 생성 없음).
        // MySQL의 INSERT ... ON DUPLICATE KEY UPDATE는 "기존 행을 실제로 바꾼 업데이트"일 때
        // 영향받은 행 수를 2로 보고한다(신규 삽입이면 1) — 실측으로 확인한 MySQL 표준 동작.
        affected shouldBe 2
        val entity = springPointJpa.findByMemberId(member.id!!).get()
        entity.point shouldBe INITIAL_BALANCE + CHARGE_AMOUNT
        springPointJpa.findAll().count { it.memberId == member.id } shouldBe 1
    }

    @Test
    fun `잔액이 충분하면 usePoint는 정상적으로 차감되고 updated_at도 함께 갱신된다`() {
        // given - chargePoint로 초기 잔액을 만든다(JPA 감사 리스너의 JVM 시계가 아니라 usePoint와
        // 같은 DB 시계(now(6))로 updated_at을 남겨야, 호스트/컨테이너 시계 차이와 무관하게
        // "이전 < 이후" 비교가 유효하다 — fixture.chargePoint()는 JPA save()라 JVM 시계를 쓴다)
        val member = fixture.createMember()
        inTx { pointRepository.chargePoint(member.id!!, INITIAL_BALANCE) }
        val beforeUpdatedAt = springPointJpa.findByMemberId(member.id!!).get().updatedAt!!
        // updated_at 갱신을 시간차로 구분하기 위한 최소 지연(이 테스트 전용 — DB datetime(6) 정밀도 대비)
        Thread.sleep(UPDATED_AT_RESOLUTION_MARGIN_MS)

        // when
        val affected = inTx { pointRepository.usePoint(member.id!!, USE_AMOUNT) }

        // then - 차감된 잔액 + updated_at이 이전보다 나중이어야 한다(JPQL bulk update는
        // @LastModifiedDate 리스너를 거치지 않아 명시적으로 세팅하지 않으면 갱신되지 않는다 — 리뷰 LOW)
        affected shouldBe 1
        val entity = springPointJpa.findByMemberId(member.id!!).get()
        entity.point shouldBe INITIAL_BALANCE - USE_AMOUNT
        entity.updatedAt!!.isAfter(beforeUpdatedAt) shouldBe true
    }

    @Test
    fun `잔액이 부족하면 usePoint는 0건을 반환하고 잔액은 그대로다`() {
        // given - 차감하려는 금액보다 적은 잔액
        val member = fixture.createMember()
        fixture.chargePoint(member.id!!, SMALL_BALANCE)

        // when
        val affected = inTx { pointRepository.usePoint(member.id!!, USE_AMOUNT) }

        // then - 조건(point >= amount)을 만족하는 행이 없어 0건, 잔액도 변하지 않는다
        affected shouldBe 0
        springPointJpa.findByMemberId(member.id!!).get().point shouldBe SMALL_BALANCE
    }

    /** `@Modifying` 조건부 UPDATE/UPSERT는 트랜잭션이 필요하다 — 운영에서는 Facade의 @Transactional 안에서 호출됨 */
    private fun <T> inTx(block: () -> T): T = transactionTemplate.execute { block() }!!

    companion object {
        private const val CHARGE_AMOUNT = 1_000
        private const val INITIAL_BALANCE = 10_000
        private const val USE_AMOUNT = 3_000
        private const val SMALL_BALANCE = 1_000
        private const val UPDATED_AT_RESOLUTION_MARGIN_MS = 5L
    }
}
