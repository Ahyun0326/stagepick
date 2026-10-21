package kr.hhplus.be.server.support

import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 모든 작업을 출발선(CountDownLatch)에 세운 뒤 동시에 시작시키고, 각 작업의 결과(또는 예외)를
 * 모아 리스트로 반환한다. `SeatRepositoryIntegrationTest`의 "락 없이 DB 방어선만 검증" 패턴을
 * 공용 헬퍼로 추출했다 (STEP4 선택 심화 ②). 결과값이 필요 없으면 `runConcurrently`를 쓴다.
 */
fun <T, R> runConcurrentlyCollecting(inputs: List<T>, awaitSeconds: Long = 30L, task: (T) -> R): List<R> {
    val executor = Executors.newFixedThreadPool(inputs.size)
    val startGate = CountDownLatch(1)
    val futures = inputs.map { input -> executor.submit<R> { startGate.await(); task(input) } }
    startGate.countDown()
    val results = futures.map { it.get(awaitSeconds, TimeUnit.SECONDS) }
    executor.shutdown()
    return results
}

/**
 * 동시성 통합 테스트 공용 헬퍼.
 *
 * 모든 작업을 출발선(CountDownLatch)에 세운 뒤 동시에 시작시켜 실제 경합을 만든다.
 * `ConcurrentSeatReservationIntegrationTest`에서 쓰던 패턴을 포인트/결제/대기열 동시성 테스트에서도
 * 재사용하기 위해 공용 헬퍼로 추출했다 (STEP4 선택 심화 ②).
 *
 * @param inputs 동시에 실행할 작업별 입력값
 * @param awaitSeconds 모든 스레드가 준비되거나 끝날 때까지 기다리는 최대 시간(초)
 * @param task 각 입력값에 대해 실행할 작업
 */
fun <T> runConcurrently(inputs: List<T>, awaitSeconds: Long = 30L, task: (T) -> Unit) {
    val executor = Executors.newFixedThreadPool(inputs.size)
    val ready = CountDownLatch(inputs.size)
    val startGate = CountDownLatch(1)
    val done = CountDownLatch(inputs.size)

    inputs.forEach { input ->
        executor.submit {
            ready.countDown()
            startGate.await()
            try {
                task(input)
            } finally {
                done.countDown()
            }
        }
    }

    ready.await(awaitSeconds, TimeUnit.SECONDS)
    startGate.countDown()
    done.await(awaitSeconds, TimeUnit.SECONDS) shouldBe true
    executor.shutdown()
}
