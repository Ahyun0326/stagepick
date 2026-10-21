package kr.hhplus.be.server.concurrency

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.member.domain.model.Member
import kr.hhplus.be.server.domains.member.domain.repository.MemberRepository
import kr.hhplus.be.server.support.runConcurrentlyCollecting
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.support.TransactionTemplate

/**
 * STEP4 선택 심화 ② 동시성 리스트업 — 회원가입 loginId 중복 (위험도: 중, 현재 동작을 고정)
 *
 * 리스트업 근거: `MemberAuthService.signUp()`은 `findByLoginId`로 중복을 먼저 확인한 뒤 `save()`한다
 * (check-then-act, 분산락 없음). 동시에 같은 loginId로 가입하면 둘 다 "중복 없음"을 보고 둘 다 저장을
 * 시도할 수 있다. `loginId` 컬럼엔 이미 `@Column(unique = true)` 제약이 있어 중복 "행"은 생기지 않지만,
 * `MemberRepository`가 Spring Data `JpaRepository`를 직접 상속하는 통합형 구조라(인덱스 보고서에서도
 * 확인한 "현상 유지" 결정) 제약 위반을 도메인 예외(`LoginIdDuplicateException`)로 변환하는 지점이 없다.
 * 그 결과 패자 트랜잭션은 `DataIntegrityViolationException`이 그대로 전파되어 `GlobalExceptionHandler`의
 * 범용 핸들러(HTTP 500)로 떨어진다.
 *
 * 판단: 데이터 정합성(중복 행 생성) 자체는 이미 안전하므로 "위험도 중"으로 분류하고 고치지 않았다 —
 * 돈·재고에 영향이 없고 UX 품질(에러 코드) 문제라 범위 밖. 아래 테스트로 **현재 동작을 고정**한다.
 */
@DisplayName("[동시성] 회원가입 loginId 중복 (위험도 중 - 현재 동작 고정)")
class MemberLoginIdConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var memberRepository: MemberRepository
    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `같은 loginId로 동시에 가입해도 중복 행은 생기지 않지만, 패자는 DataIntegrityViolationException을 그대로 받는다`() {
        // given
        val loginId = "dup-user-${System.nanoTime()}"

        // when - 같은 loginId로 동시에 여러 번 저장 시도 (분산락 없음, UNIQUE 제약만 방어선)
        val results = runConcurrentlyCollecting((1..ATTEMPT_COUNT).toList()) {
            runCatching {
                transactionTemplate.execute {
                    memberRepository.save(Member.create(loginId, "encoded", "테스트유저"))
                }
            }
        }

        // then 1 - UNIQUE 제약 덕분에 DB에는 정확히 한 행만 남는다 (데이터 정합성은 안전)
        results.count { it.isSuccess } shouldBe 1
        memberRepository.findByLoginId(loginId).shouldNotBeNull()

        // then 2 - 패자는 도메인 예외(LoginIdDuplicateException)가 아니라 번역된 DB 예외를 그대로 받는다
        // — 바꾸려면 MemberAuthService나 Repository 계층에서 변환이 필요하다 (현재는 범위 밖, 고정만 함)
        results.filter { it.isFailure }.forEach { result ->
            result.exceptionOrNull().shouldBeInstanceOf<DataIntegrityViolationException>()
        }
    }

    companion object {
        private const val ATTEMPT_COUNT = 10
    }
}
