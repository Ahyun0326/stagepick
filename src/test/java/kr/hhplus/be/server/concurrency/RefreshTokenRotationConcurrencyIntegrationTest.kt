package kr.hhplus.be.server.concurrency

import io.kotest.matchers.shouldBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.common.jwt.JwtProvider
import kr.hhplus.be.server.common.jwt.RefreshTokenRepository
import kr.hhplus.be.server.domains.member.application.MemberAuthService
import kr.hhplus.be.server.support.fixture.ConcertReservationFixture
import kr.hhplus.be.server.support.runConcurrentlyCollecting
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * STEP4 선택 심화 ② 동시성 리스트업 — 리프레시 토큰 회전(rotation) 경합
 *
 * **새로 발견 — 사용자 결정 필요** (docs/report/step4-concurrency-report.md §5 참고).
 * 기존 리스트업(§2 표)은 대기열·포인트·결제·회원가입만 다뤘고 `MemberAuthService.refresh()`는
 * 점검하지 않았었다. 이번 전면 재점검(1번 과제)에서 새로 찾았다.
 *
 * `MemberAuthService.refresh(refreshToken)`은
 *   1) `refreshTokenRepository.find(uuid)`로 저장된 값을 읽고
 *   2) 요청받은 토큰과 같은지 비교한 뒤
 *   3) 새 리프레시 토큰을 만들어 `save(uuid, newToken)`로 **덮어쓴다**(check-then-act, 락 없음,
 *      회원당 Redis key 1개뿐).
 * 같은 refreshToken으로 동시에 두 번 요청하면(네트워크 재시도, 앱 재시작 시 중복 호출 등) 두 요청
 * 모두 1)에서 "아직 유효함"을 확인하고 둘 다 자신만의 새 토큰을 만들어 저장을 시도할 수 있다.
 * Redis key는 회원당 1개뿐이라 **나중에 저장한 쪽만 남고 먼저 저장한 쪽은 조용히 사라진다** —
 * 먼저 응답을 받은 클라이언트가 "방금 발급받은 리프레시 토큰"을 들고 있어도, 그 토큰으로 다음 갱신을
 * 시도하면 이미 다른 토큰으로 덮어써졌기 때문에 `InvalidTokenException`(재로그인 필요)을 받는다.
 *
 * 돈·재고 영향은 없지만 로그인 세션에는 영향이 있다(사용자가 갑자기 로그아웃되는 것처럼 보임).
 * 고치지 않았다 — 가능한 대안: (a) 새 리프레시 토큰을 발급할 때 이전 토큰을 즉시 폐기하지 않고
 * 짧은 grace period 동안 유효하게 두는 방식, (b) uuid당 Redis 분산락으로 `find`→`save`를 직렬화.
 */
@DisplayName("[동시성] 리프레시 토큰 회전(rotation) 경합 - 새로 발견")
class RefreshTokenRotationConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var fixture: ConcertReservationFixture
    @Autowired private lateinit var memberAuthService: MemberAuthService
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var jwtProvider: JwtProvider

    @Test
    fun `같은 리프레시 토큰으로 동시에 두 번 갱신하면 한쪽의 새 토큰은 저장되지 않고 사라질 수 있다 (현재 동작 고정 - 새로 발견, 위험 인지)`() {
        // given - 이미 로그인한 회원의 유효한 리프레시 토큰 1개
        val member = fixture.createMember()
        val refreshToken = jwtProvider.generateRefreshToken(member.uuid)
        refreshTokenRepository.save(member.uuid, refreshToken)

        // when - 같은 refreshToken으로 두 요청이 동시에 갱신을 시도 (중복 탭, 네트워크 재시도 등)
        val results = runConcurrentlyCollecting(listOf(1, 2)) {
            runCatching { memberAuthService.refresh(refreshToken) }
        }

        // then - 방어(락)가 없어 둘 다 "아직 유효함"을 통과해 예외 없이 성공한다
        // (원래 리프레시 토큰 회전은 1회성으로 설계된 것처럼 보이지만, 동시 요청에는 그 보장이 없다).
        results.count { it.isSuccess } shouldBe 2

        val issuedRefreshTokens = results.map { it.getOrThrow().second }
        val storedAfterBoth = refreshTokenRepository.find(member.uuid)

        // Redis key가 회원당 1개뿐이라 최종적으로 남는 값은 "두 응답 중 하나"뿐이다 — 즉 적어도 한쪽
        // 응답의 토큰은 이미 유령(저장되지 않음) 상태라는 뜻이다. (두 토큰이 같은 밀리초에 발급돼
        // 우연히 동일 문자열이 되는 극히 드문 경우는 손실이 눈에 보이지 않을 수 있다 — 보고서에 기록)
        (storedAfterBoth == issuedRefreshTokens[0] || storedAfterBoth == issuedRefreshTokens[1]) shouldBe true

        // 진 쪽의 토큰으로 "다음" 갱신을 시도하면 더는 저장된 값과 일치하지 않아 거절된다는 것을 보여준다.
        // 두 토큰이 같은 밀리초에 발급돼 우연히 같은 문자열이 된 경우(손실이 눈에 보이지 않는 경우)는
        // 진 쪽이 없으므로 이 확인을 건너뛴다 — 그 자체도 보고서에 실측으로 기록한다.
        val loserToken = issuedRefreshTokens.firstOrNull { it != storedAfterBoth }
        if (loserToken != null) {
            runCatching { memberAuthService.refresh(loserToken) }.isFailure shouldBe true
        }
    }
}
