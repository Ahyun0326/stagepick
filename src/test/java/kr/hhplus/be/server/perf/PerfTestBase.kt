package kr.hhplus.be.server.perf

import kr.hhplus.be.server.IntegrationTestConfiguration
import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * 성능 측정(perf) 테스트 전용 베이스 클래스.
 *
 * [IntegrationTestBase]와 달리 매 테스트마다 테이블을 TRUNCATE 하지 않는다.
 * 대량 시드 데이터(수십만 건)를 매번 재적재하면 측정 자체보다 시딩 시간이 더 커지기 때문에,
 * 테스트 클래스(혹은 스위트) 단위로 한 번만 시딩하고 여러 쿼리를 측정한다.
 *
 * 같은 [kr.hhplus.be.server.TestcontainersConfiguration] 싱글톤 컨테이너를 재사용하므로
 * 기본 `./gradlew test`와 동일한 MySQL 8.0 / Redis 버전으로 측정된다.
 *
 * `@Tag("perf")`가 붙은 테스트만 `perfTest` 태스크에서 실행되고,
 * 기본 `test` 태스크(`build.gradle.kts`에서 `excludeTags("perf")`)에서는 제외된다.
 */
@Tag("perf")
@SpringBootTest
@ActiveProfiles("integration")
@Import(IntegrationTestConfiguration::class)
abstract class PerfTestBase
