package kr.hhplus.be.server.concurrency

import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.support.runConcurrentlyCollecting
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

/**
 * STEP4 선택 심화 ② 동시성 리스트업 — 대기열 토큰 발급 정원
 *
 * 리스트업 근거: 기존 `IssueQueueTokenService.issue()`는 `activeQueueRepository.countActive(scheduleId)`로
 * 현재 활성 인원을 조회한 뒤, 그 결과로 판단해 `saveActive()`를 호출했다(check-then-act). 두 Redis 호출
 * 사이에 다른 요청이 끼어들 수 있어, 정원 바로 아래에서 여러 명이 동시에 발급을 시도하면 모두 "정원 이하"로
 * 판단되어 정원을 초과해 발급될 수 있다. 같은 회원이 토큰을 동시에 두 번 요청해도(재시도, 멀티 탭 등)
 * 카운트/저장이 원자적이지 않아 토큰이 중복 집계될 수 있다.
 *
 * 방어선: `ActiveQueueRepository.tryIssueActive` — 정원 확인(ZCOUNT)과 등록(SADD+ZADD+SET)을 Redis
 * Lua 스크립트 한 번의 EVAL로 묶어 원자화했다. Redis는 EVAL을 한 번에 하나씩만 실행하므로
 * 여러 스레드가 동시에 호출해도 "확인 후 등록" 사이에 다른 호출이 끼어들 수 없다.
 */
@DisplayName("[동시성] 대기열 토큰 발급 정원")
class QueueCapacityConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var activeQueueRepository: ActiveQueueRepository

    @Test
    fun `정원 바로 아래에서 여러 명이 동시에 발급을 시도해도 활성 인원은 정원을 넘지 않는다`() {
        // given - 스케줄 1개, 정원보다 훨씬 많은 신규 사용자(= 전부 다른 uuid)가 동시에 발급 시도
        val scheduleId = 1L
        val uuids = (1..REQUEST_COUNT).map { UUID.randomUUID().toString() }

        // when - 모두가 같은 순간에 활성 토큰 발급을 시도
        val admittedResults = runConcurrentlyCollecting(uuids) { uuid ->
            activeQueueRepository.tryIssueActive(scheduleId, uuid, CAPACITY)
        }

        // then - 정확히 정원(CAPACITY)명만 발급되고, 나머지는 거절되어 활성 인원이 정원을 넘지 않는다
        admittedResults.count { it } shouldBe CAPACITY.toInt()
        activeQueueRepository.countActive(scheduleId) shouldBeLessThanOrEqualTo CAPACITY
    }

    @Test
    fun `같은 사용자가 동시에 여러 번 발급을 요청해도 활성 인원은 1명만 집계된다`() {
        // given - 스케줄 1개, 같은 uuid로 동시에 여러 번 발급 요청 (재시도·멀티 탭 등)
        val scheduleId = 2L
        val uuid = UUID.randomUUID().toString()
        val attempts = (1..SAME_USER_ATTEMPT_COUNT).toList()

        // when
        val admittedResults = runConcurrentlyCollecting(attempts) {
            activeQueueRepository.tryIssueActive(scheduleId, uuid, CAPACITY)
        }

        // then - 모든 시도가 "활성 상태 보장"(true)을 받지만, 실제 활성 인원은 1명뿐이다
        admittedResults.all { it } shouldBe true
        activeQueueRepository.countActive(scheduleId) shouldBe 1L
    }

    companion object {
        private const val CAPACITY = 5L
        private const val REQUEST_COUNT = 20
        private const val SAME_USER_ATTEMPT_COUNT = 10
    }
}
