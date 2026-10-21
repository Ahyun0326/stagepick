package kr.hhplus.be.server.concurrency

import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.point.application.dto.request.ChargePointRequest
import kr.hhplus.be.server.domains.point.application.facade.PointFacade
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import kr.hhplus.be.server.support.runConcurrentlyCollecting
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * 코드 리뷰(docs/agent/reviews/2026-10-21-concurrency-review.md) MEDIUM 항목 — 일회성 조사.
 *
 * "신규 회원(포인트 행 없음) 동시 충전은 `INSERT ... ON DUPLICATE KEY UPDATE`로 처리되는데,
 * 해당 member_id 유니크 인덱스에 아직 행이 없는 상태에서 여러 트랜잭션이 같은 키로 동시에
 * INSERT를 시도하면 InnoDB가 갭락(gap lock) 경합으로 데드락(에러 1213, "Deadlock found when
 * trying to get lock; try restarting transaction")을 낼 수 있다"는 지적이 재현되는지, 스레드 수를
 * 30/40/50으로 늘려 반복 시도해 실측한다.
 *
 * - 운영 HikariCP 풀 크기(3, §10-1에 "사용자 결정 대기"로 남겨둔 별개 이슈)가 섞이면 "연결을
 *   못 받아서 느려지는 것"과 "DB 레벨 데드락"을 구분할 수 없으므로, 이 테스트에서만
 *   `@TestPropertySource`로 풀 크기를 넉넉하게 올려 변수를 분리한다. 운영 설정 자체는 건드리지 않는다.
 * - 재현 여부와 무관하게 테스트를 실패시키지 않는다 — 관찰 자체가 목적이며, 결과(빈도·예외 타입)는
 *   표준출력과 `docs/report/step4-concurrency-report.md`에 기록한다. 대응(재시도 도입 등)은 구현하지
 *   않고 보고서에 제안만 남긴다 — 메인 세션이 최종 판단한다.
 * - 스레드 수가 크고 타이밍에 의존적이라 시간이 걸릴 수 있어 `@Tag("perf")`로 분리했다
 *   (`./gradlew test`에서는 제외, `./gradlew perfTest`로만 실행 — `build.gradle.kts` 참고).
 */
@Tag("perf")
@TestPropertySource(properties = ["spring.datasource.hikari.maximum-pool-size=60"])
@DisplayName("[일회성 조사] 포인트 UPSERT(INSERT ON DUPLICATE KEY UPDATE) 데드락 재현 시도")
class PointUpsertDeadlockReproductionTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture
    @Autowired private lateinit var pointFacade: PointFacade

    @Test
    fun `신규 회원 동시 최초 충전을 스레드 수 30, 40, 50으로 각각 여러 번 시도해 데드락 재현 여부를 기록한다`() {
        val allLines = mutableListOf<String>()

        for (threadCount in THREAD_COUNTS) {
            repeat(ITERATIONS_PER_THREAD_COUNT) { iteration ->
                // given - 포인트 행이 전혀 없는 신규 회원 (매 반복마다 새로 만들어야 "최초 충전" 경합이 유지됨)
                val member = fixture.createMember()

                // when - threadCount개 스레드가 같은 신규 회원에게 동시에 최초 충전 시도
                val results = runConcurrentlyCollecting((1..threadCount).toList()) {
                    runCatching { pointFacade.chargePoint(member.uuid, ChargePointRequest(CHARGE_AMOUNT)) }
                }

                val failures = results.filter { it.isFailure }
                val exceptionTypeCounts = failures
                    .groupBy { rootCauseOf(it.exceptionOrNull()!!).javaClass.name }
                    .mapValues { (_, v) -> v.size }

                val line = "threads=$threadCount iter=${iteration + 1} success=${results.size - failures.size}/$threadCount " +
                    "failures=${failures.size} exceptionTypes=$exceptionTypeCounts"
                println("DEADLOCK-REPRO $line")
                allLines += line

                failures.take(SAMPLE_MESSAGE_LIMIT).forEach {
                    val root = rootCauseOf(it.exceptionOrNull()!!)
                    println("DEADLOCK-REPRO   sample cause=${root.javaClass.name} message=${root.message}")
                }
            }
        }

        println("DEADLOCK-REPRO ===== SUMMARY (threads=$THREAD_COUNTS x ${ITERATIONS_PER_THREAD_COUNT}회) =====")
        allLines.forEach { println("DEADLOCK-REPRO $it") }
    }

    /** Spring/Hibernate가 감싼 예외 체인을 끝까지 풀어 실제 JDBC/SQL 예외를 찾는다 */
    private fun rootCauseOf(e: Throwable): Throwable {
        var current = e
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current
    }

    companion object {
        private const val CHARGE_AMOUNT = 1_000
        private val THREAD_COUNTS = listOf(30, 40, 50)
        private const val ITERATIONS_PER_THREAD_COUNT = 5
        private const val SAMPLE_MESSAGE_LIMIT = 3
    }
}
