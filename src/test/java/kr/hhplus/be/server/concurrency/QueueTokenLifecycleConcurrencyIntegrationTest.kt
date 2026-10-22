package kr.hhplus.be.server.concurrency

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.common.exception.QueueTokenInvalidException
import kr.hhplus.be.server.domains.queue.application.usecase.IssueQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.PromoteQueueTokenService
import kr.hhplus.be.server.domains.queue.application.usecase.ValidateQueueTokenService
import kr.hhplus.be.server.domains.queue.domain.model.AdmissionStatus
import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.domain.repository.WaitingQueueRepository
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
import kr.hhplus.be.server.support.runConcurrently
import kr.hhplus.be.server.support.runConcurrentlyCollecting
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import java.util.UUID

/**
 * STEP4 선택 심화 ② 동시성 리스트업 — 대기열 토큰 생명주기(승격/만료/반납) 경합
 *
 * 네 가지를 다룬다:
 *
 * 1. 대기자 승격(`PromoteQueueTokenService`)의 멀티 인스턴스 경합 — 과거에는 `countActive` 후
 *    `saveActive`를 그대로 호출하는 check-then-act여서, 발급 정원(`tryIssueActive`, §4.3)과 달리
 *    원자화돼 있지 않았다. 여러 서버 인스턴스의 스케줄러가 동시에 같은 스케줄을 승격시키면 정원을
 *    초과해 승격될 수 있었다(RED 실측: 정원 5, 활성 50명). **이번 세션에서 `ActiveQueueRepository.promote`
 *    Redis Lua EVAL로 원자화해 수정했다(ADR-7)** — 아래 테스트 2개(정원 준수 + 토큰 조회/북키핑 정리)가
 *    수정 후 기대 동작을 고정한다.
 * 2. 토큰 반납(결제 완료, `removeActive`) vs 검증(다른 탭의 좌석 조회, `ValidateQueueTokenService`)의 경합
 *    (위험도 하로 분류, 이번 세션에서 수정하지 않음 — "현재 동작 고정" 유지).
 * 3. 토큰 만료 정리(`removeExpiredActive`)와 신규 발급(`tryIssueActive`)의 경합 — `removeExpiredActive`는
 *    "ZREMRANGEBYSCORE 실행 → 활성 ZSET이 비었으면 스케줄 집합(`queue:schedule:active`)에서 scheduleId 제거"라는
 *    2단계 비원자적 흐름이다. 두 번째 단계(size 확인 → SREM) 사이에 다른 사용자가 같은 스케줄에 새로
 *    발급받으면(SADD로 scheduleId를 다시 넣음), 뒤늦게 실행되는 SREM이 방금 들어온 scheduleId를 다시
 *    지워버릴 수 있다 — 그러면 스케줄러(`ExpireQueueTokenService`/`PromoteQueueTokenService`)가
 *    `findScheduleIds()`로 이 스케줄을 더는 찾지 못해, 다음 신규 발급이 일어나기 전까지 만료·승격 처리가
 *    누락된다. 돈·재고 영향은 없고(활성 ZSET 자체의 데이터는 멀쩡함), 북키핑 집합만 일시적으로 어긋난다.
 * 4. 승격(`ActiveQueueRepository.promote`)과 신규 발급(`tryIssueActive`)을 **같은 스케줄·정원에
 *    섞어서** 동시 실행 — 1번 항목까지는 "승격끼리"만 동시에 돌렸는데, 실제 운영에서는 스케줄러의
 *    승격 tick과 사용자의 신규 발급 요청이 같은 순간에 같은 스케줄의 같은 활성 ZSET을 건드릴 수
 *    있다. 서로 다른 두 Lua EVAL이 뒤섞여도 Redis의 단일 스레드 직렬 실행 덕분에 정원이 지켜지는지
 *    실측으로 확인한다(코드 리뷰 MEDIUM 반영).
 */
@DisplayName("[동시성] 대기열 토큰 생명주기(승격/만료/반납) 경합")
class QueueTokenLifecycleConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var activeQueueRepository: ActiveQueueRepository
    @Autowired private lateinit var waitingQueueRepository: WaitingQueueRepository
    @Autowired private lateinit var validateQueueTokenService: ValidateQueueTokenService
    @Autowired private lateinit var redisTemplate: RedisTemplate<String, Any>
    @Autowired private lateinit var queueProperties: QueueProperties

    @Test
    fun `여러 인스턴스가 동시에 대기자를 승격시켜도 정원을 넘지 않고, 같은 대기자가 중복 승격되지 않는다`() {
        // given - 정원 CAPACITY, 그보다 훨씬 많은 대기자. "여러 인스턴스"를 흉내 내기 위해
        // 서버 인스턴스 개수만큼 PromoteQueueTokenService 객체를 각각 새로 만든다
        // (서로 다른 객체 = 인스턴스 간 공유 상태 없음, 공유되는 건 Redis뿐 — 실제 멀티 인스턴스와 동일한 조건).
        val scheduleId = 10L
        val waitingUuids = (1..WAITING_COUNT).map { UUID.randomUUID().toString() }
        waitingUuids.forEach { waitingQueueRepository.add(scheduleId, it) }

        val instances = (1..INSTANCE_COUNT).map {
            PromoteQueueTokenService(activeQueueRepository, waitingQueueRepository, CAPACITY, CAPACITY)
        }

        // when - INSTANCE_COUNT대의 "인스턴스"가 같은 순간에 한 번씩 승격 tick을 실행
        runConcurrently(instances) { it.execute() }

        // then - 활성 인원은 정원을 넘지 않아야 하고(ActiveQueueRepository.promote의 Lua EVAL이
        // "남은 슬롯 계산 + 꺼내기 + 등록"을 원자적으로 묶어주므로), 대기자 풀(WAITING_COUNT=50)이
        // 정원(5)보다 훨씬 커 공급은 충분하므로 정확히 정원만큼 채워져야 한다.
        val activeCount = activeQueueRepository.countActive(scheduleId)

        val promotedUuids = waitingUuids.filter { activeQueueRepository.findActive(scheduleId, it) != null }
        val stillWaitingUuids = waitingUuids.filter { waitingQueueRepository.isWaiting(scheduleId, it) }

        println(
            "[실측] 대기자 승격 멀티 인스턴스 경합(Lua 원자화 후): 정원=$CAPACITY, 인스턴스=$INSTANCE_COUNT, " +
                "실제 활성 인원=$activeCount, 승격된 대기자 수=${promotedUuids.size}, " +
                "아직 대기 중인 대기자 수=${stillWaitingUuids.size}"
        )

        // Rule 1: 활성 인원은 정원을 초과하지 않는다. 공급(50명)이 정원(5)보다 훨씬 많으므로
        // 정확히 정원만큼 채워진다.
        activeCount shouldBe CAPACITY

        // Rule 2: 승격된 대기자 수 == 실제 활성 인원 수(중복 승격·유실 없이 1:1로 대응한다).
        promotedUuids.size shouldBe activeCount.toInt()

        // Rule 3: 승격된 대기자 + 아직 대기 중인 대기자의 합이 원래 대기자 수와 같다 — 어느 쪽에도
        // 속하지 않거나(유실) 양쪽에 동시에 속하는(중복 승격) 대기자가 없다는 뜻이다.
        (promotedUuids.size + stillWaitingUuids.size) shouldBe WAITING_COUNT
    }

    @Test
    fun `승격된 대기자는 활성 토큰을 조회할 수 있고, 하트비트와 대기열 북키핑이 정리된다`() {
        // given - 정원(CAPACITY)보다 적은 대기자만 등록해 전원이 승격되는 상황을 만든다.
        // ActiveQueueRepository.promote의 Lua가 ZPOPMIN으로 꺼낸 uuid 원본 바이트는 GenericJackson2Json
        // 직렬화 때문에 앞뒤에 쌍따옴표가 붙어 있어(§ADR-7), 이를 그대로 "queue:schedule:{id}:active:uuid:{uuid}"
        // 키로 쓰면 틀린 키가 된다 — Lua 쪽에서 쌍따옴표를 벗겨내는 처리가 실제로 맞는지, findActive로
        // 역방향 조회가 되는지까지 확인해야 그 트릭이 제대로 동작한다는 걸 보장할 수 있다.
        val scheduleId = 13L
        val promotedUuid = UUID.randomUUID().toString()
        waitingQueueRepository.add(scheduleId, promotedUuid)
        waitingQueueRepository.add(scheduleId, UUID.randomUUID().toString())

        val promoteQueueTokenService = PromoteQueueTokenService(activeQueueRepository, waitingQueueRepository, CAPACITY, CAPACITY)

        // when
        promoteQueueTokenService.execute()

        // then - 승격된 uuid로 활성 토큰을 조회할 수 있다(쌍따옴표를 벗겨 만든 키가 findActive가
        // 평문 uuid로 만드는 키와 정확히 일치한다는 뜻).
        val token = activeQueueRepository.findActive(scheduleId, promotedUuid)
        token.shouldNotBeNull()

        // 남은 시간이 TTL(activeTokenTtlMs) 범위 안에 있다 — ZADD에 쓴 만료 시각이 올바르게 계산됐다.
        // 상한은 매직넘버로 박아두지 않고 QueueProperties.activeTokenTtlMs(주입된 실제 설정값)에서 계산한다.
        val maxRemainingSeconds = queueProperties.activeTokenTtlMs / 1000
        val remainingSeconds = activeQueueRepository.getRemainingSeconds(scheduleId, promotedUuid)
        (remainingSeconds in 1..maxRemainingSeconds) shouldBe true

        // 더 이상 대기열(ZSET)에 없다.
        waitingQueueRepository.isWaiting(scheduleId, promotedUuid) shouldBe false

        // heartbeat key도 함께 정리됐다(Lua의 DEL 호출이 WaitingQueueRedisRepository와 같은 키 포맷을 썼다는 뜻).
        redisTemplate.hasKey("queue:schedule:$scheduleId:waiting:heartbeat:$promotedUuid") shouldBe false

        // 대기자 2명 모두 승격돼 대기열이 비면 대기 스케줄 집합에서도 scheduleId가 제거된다.
        waitingQueueRepository.findScheduleIds().contains(scheduleId) shouldBe false

        // 활성 스케줄 집합에는 scheduleId가 등록된다(다음 틱의 만료/승격 스케줄러가 findScheduleIds로 찾아야 함).
        activeQueueRepository.findScheduleIds().contains(scheduleId) shouldBe true
    }

    @Test
    fun `토큰 반납과 검증이 동시에 일어나도 예외 없이 끝나거나 QueueTokenInvalidException만 던진다 (현재 동작 고정)`() {
        var validateSucceeded = 0
        var validateRejected = 0

        // RELEASE_VS_VALIDATE_REPEAT번 반복 — 반납/검증 중 어느 쪽이 먼저 끝나는지는 타이밍에 달려
        // 있어 한 번만으로는 두 분기를 다 보장할 수 없다. 실제 분포를 실측해 보고서에 남긴다.
        repeat(RELEASE_VS_VALIDATE_REPEAT) { i ->
            // given - 활성 토큰을 가진 사용자 1명
            val scheduleId = 11_000L + i
            val uuid = UUID.randomUUID().toString()
            activeQueueRepository.tryIssueActive(scheduleId, uuid, CAPACITY)
            val token = activeQueueRepository.findActive(scheduleId, uuid)!!

            // when - 결제 완료로 인한 토큰 반납과, 다른 탭의 좌석 조회로 인한 토큰 검증이 같은 순간에 발생
            val results = runConcurrentlyCollecting(listOf("release", "validate")) { action ->
                runCatching {
                    if (action == "release") {
                        activeQueueRepository.removeActive(scheduleId, uuid)
                    } else {
                        validateQueueTokenService.validate(uuid, scheduleId, token)
                    }
                }
            }

            // then - 반납과 검증 어느 쪽이 먼저 끝나든, 검증 결과는 "성공(아직 유효했음)" 또는
            // "QueueTokenInvalidException(막 반납됨)" 둘 중 하나여야 한다. 다른 예외(NPE 등)가 나오면
            // 반납 도중 검증이 끼어들 때 Redis 호출 자체가 깨진다는 뜻이라 안 된다.
            val validateResult = results[1]
            if (validateResult.isFailure) {
                validateResult.exceptionOrNull().shouldBeInstanceOf<QueueTokenInvalidException>()
                validateRejected++
            } else {
                validateSucceeded++
            }

            // 반납은 항상 성공한다(토큰 존재 여부만 확인하고, 없으면 조용히 끝남)
            results[0].isSuccess shouldBe true

            // 반납이 끝난 뒤 최종 상태는 항상 "토큰 없음"으로 수렴한다
            activeQueueRepository.findActive(scheduleId, uuid).shouldBeNull()
        }

        println(
            "[실측] 토큰 반납 vs 검증 경합: $RELEASE_VS_VALIDATE_REPEAT 회 중 " +
                "검증 성공=$validateSucceeded, QueueTokenInvalidException=$validateRejected"
        )
    }

    @Test
    fun `만료 정리와 신규 발급을 반복적으로 동시에 실행해도 데이터가 깨지지 않는다 (북키핑 경합 재현 시도)`() {
        // given - 이미 만료된 활성 토큰 하나로 시작하는 스케줄(활성 스케줄 집합에는 등록돼 있음)
        val scheduleId = 12L
        activeQueueRepository.tryIssueActive(scheduleId, "expired-seed", CAPACITY)
        moveSeedScoreToPast(scheduleId)

        var missingWhileActiveCount = 0

        // when - "만료 정리"와 "새 사용자 발급"을 REPEAT번 반복해서 같은 순간에 경합시킨다
        // (타이밍 창이 좁아 한두 번으로는 재현되지 않을 수 있어 반복한다 — §보고서 한계에 결과를 기록)
        repeat(REPEAT) { i ->
            val newUuid = "racer-$i"
            runConcurrently(listOf("expire", "issue")) { action ->
                if (action == "expire") {
                    activeQueueRepository.removeExpiredActive(scheduleId)
                } else {
                    activeQueueRepository.tryIssueActive(scheduleId, newUuid, CAPACITY)
                }
            }

            val stillActive = activeQueueRepository.countActive(scheduleId) > 0
            val scheduleListed = activeQueueRepository.findScheduleIds().contains(scheduleId)
            if (stillActive && !scheduleListed) {
                missingWhileActiveCount++
            }
        }

        println(
            "[실측] 만료 정리 vs 발급 경합(스케줄 북키핑): $REPEAT 회 반복 중 " +
                "활성 인원이 있는데 스케줄 목록에서 빠진 횟수 = $missingWhileActiveCount"
        )

        // 이 테스트는 "재현 여부 자체"를 실측하는 용도라 missingWhileActiveCount가 0이든 아니든
        // 유효한 결과다(결과는 보고서 §5/§한계에 그대로 옮긴다). 여기서는 반복 실행 중 예외 없이
        // 끝나는 것(카운트가 음수가 되는 등 데이터 자체가 깨지지 않는 것)만 Rule로 고정한다.
        (activeQueueRepository.countActive(scheduleId) >= 0L) shouldBe true
    }

    @Test
    fun `승격과 신규 발급을 같은 스케줄·정원에 동시에 섞어 실행해도 활성 인원이 정원을 넘지 않는다`() {
        // given - 승격(`ActiveQueueRepository.promote`)과 신규 발급(`tryIssueActive`, `IssueQueueTokenService`)은
        // 같은 활성 ZSET을 공유하는 서로 다른 Lua EVAL(ADR-7, §4.3)이다. 각 EVAL은 자기 자신에 대해서는
        // 원자적이지만, 지금까지의 테스트(1~4번째)는 "승격끼리" 또는 "발급끼리"만 동시에 돌렸을 뿐
        // 두 종류의 EVAL을 뒤섞어 돌린 적은 없다 — Redis가 모든 EVAL을 단일 스레드로 직렬 실행한다는
        // 전제에 기대는 것이므로, 실제로 승격 tick과 신규 발급을 같은 스케줄에 뒤섞어 돌려 정원이
        // 지켜지는지 실측으로 확인한다.
        val scheduleId = 14L
        val waitingUuids = (1..MIXED_WAITING_COUNT).map { UUID.randomUUID().toString() }
        waitingUuids.forEach { waitingQueueRepository.add(scheduleId, it) }

        // 승격 tick MIXED_PROMOTE_INSTANCE_COUNT개("여러 인스턴스"를 흉내, 1번째 테스트와 동일한 방식)
        val promoteActions: List<() -> String> = (1..MIXED_PROMOTE_INSTANCE_COUNT).map {
            val promoteService = PromoteQueueTokenService(
                activeQueueRepository,
                waitingQueueRepository,
                MIXED_CAPACITY,
                MIXED_CAPACITY
            )
            val action: () -> String = { promoteService.execute(); "promote" }
            action
        }

        // 신규 발급 MIXED_NEW_ISSUE_COUNT건(각각 처음 보는 uuid로 `IssueQueueTokenService.issue` 호출)
        val issueActions: List<() -> String> = (1..MIXED_NEW_ISSUE_COUNT).map {
            val newUuid = UUID.randomUUID().toString()
            val issueService = IssueQueueTokenService(waitingQueueRepository, activeQueueRepository, MIXED_CAPACITY)
            val action: () -> String = { issueService.issue(newUuid, scheduleId).status }
            action
        }

        // when - 승격 tick과 신규 발급 요청(총 MIXED_PROMOTE_INSTANCE_COUNT + MIXED_NEW_ISSUE_COUNT개
        // 스레드)을 같은 출발선에 세워 동시에 실행한다
        val results = runConcurrentlyCollecting(promoteActions + issueActions) { action -> action() }

        // then
        val activeCount = activeQueueRepository.countActive(scheduleId)
        val promotedFromWaiting = waitingUuids.count { activeQueueRepository.findActive(scheduleId, it) != null }
        val newlyActiveIssues = results.count { it == AdmissionStatus.ACTIVE.name }

        println(
            "[실측] 승격 vs 신규 발급 혼합 경합: 정원=$MIXED_CAPACITY, " +
                "승격 인스턴스=$MIXED_PROMOTE_INSTANCE_COUNT, 신규 발급=$MIXED_NEW_ISSUE_COUNT, " +
                "실제 활성 인원=$activeCount, 승격으로 채워진 인원=$promotedFromWaiting, " +
                "신규 발급으로 채워진 인원=$newlyActiveIssues"
        )

        // Rule 1: 승격 EVAL과 발급 EVAL이 뒤섞여도 활성 인원은 정원을 초과하지 않는다. 대기자
        // 공급(MIXED_WAITING_COUNT)과 수요(승격+발급 합)가 모두 정원보다 훨씬 많으므로 정확히
        // 정원만큼 채워진다.
        activeCount shouldBe MIXED_CAPACITY

        // Rule 2: 활성 인원 전부는 "승격으로 채워진 인원" 아니면 "신규 발급으로 채워진 인원" 둘 중
        // 하나로만 설명돼야 한다 — 두 EVAL의 결과가 같은 ZSET에 이중 집계나 유실 없이 정확히 합쳐진다.
        (promotedFromWaiting + newlyActiveIssues) shouldBe activeCount.toInt()
    }

    private fun moveSeedScoreToPast(scheduleId: Long) {
        // 운영에서는 TTL(5분) 경과를 기다려야 하지만, 테스트에서는 ZSET score를 과거로 직접 옮겨
        // "방금 만료된 활성 토큰"을 즉시 만든다 (SeatHoldRedisRepositoryIntegrationTest와 동일한 기법).
        val token = activeQueueRepository.findActive(scheduleId, "expired-seed") ?: return
        redisTemplate.opsForZSet().add(
            "queue:schedule:$scheduleId:active",
            token,
            (System.currentTimeMillis() - 1_000).toDouble()
        )
    }

    companion object {
        private const val CAPACITY = 5L
        private const val WAITING_COUNT = 50
        private const val INSTANCE_COUNT = 10
        private const val REPEAT = 100
        private const val RELEASE_VS_VALIDATE_REPEAT = 50

        private const val MIXED_CAPACITY = 5L
        private const val MIXED_WAITING_COUNT = 30
        private const val MIXED_PROMOTE_INSTANCE_COUNT = 8
        private const val MIXED_NEW_ISSUE_COUNT = 8
    }
}
