# STEP 4 선택 심화 ② — 주요 기능별 동시성 테스트

> 항해플러스 STEP 4 "DB 구조로 데이터 정합성 확보" 선택 심화 ②.
> 채점 기준: *"동시성 테스트는 성공하는 것이 목적이 아니라, 어떤 기능에 동시성 이슈가 예민할지
> 미리 리스트업하고 작성하여 Rule로 가두는 것을 목적으로 합니다."*
> 이 문서의 모든 수치는 `./gradlew test`로 실제 실행해 측정한 값이며, 측정하지 않은 수치는 적지 않았다.
> 재현(RED)은 실제로 git stash로 수정 전 코드를 복원해 돌린 결과이고, 수정 후(GREEN)도 같은 방식으로
> 재확인했다 — 추측이 아니라 두 상태를 모두 실행해서 비교했다.

---

## 1. 요약

1. **좌석 예약·결제 확정/만료 해제는 이미 완료된 영역**이다(필수 ①②③, 분산락 + 조건부 UPDATE + Redis Lua). 이번 선택 심화 ②는 그 바깥의 세 영역 — **포인트, 결제 멱등성, 대기열 정원** — 에서 동시성 위험을 리스트업하고 실측으로 검증했다.
2. **셋 다 "위험도 상"으로 분류**했고, 전부 **RED(재현 또는 재현 시도) → 수정 → GREEN**의 실측 사이클을 돌렸다.
   - **포인트 충전**: 원본 코드(`find → 메모리에서 계산 → save()`)는 신규 회원 동시 충전 시 실제로 **중복 행 3개가 생성**되는 것을 실측으로 확인했다(`IncorrectResultSizeDataAccessException`). 조건부 UPDATE로 바꾸는 중간 단계에서 **또 다른 버그를 하나 더 발견**했다 — 자세한 내용은 §4.1.
   - **결제 멱등성**: 분산락이 **이미 완전히 직렬화**하고 있어 정상 시나리오에서는 재현되지 않았다(실측 확인). 그럼에도 "DB가 마지막 방어선" 원칙에 따라 `payment.reservation_id`에 UNIQUE 제약을 추가했고, **락을 거치지 않고 저장소를 직접 두드리면 원본 코드가 중복 결제 10건을 모두 허용**하는 것을 실측으로 확인했다(§4.2).
   - **대기열 정원**: `countActive` 후 `saveActive`를 따로 호출하는 원본 코드는 정원 5명에 **20명 중 18명이 발급**되는 정원 초과를 실측으로 재현했다(§4.3).
3. **리스트업 과정에서 포인트 쪽에 원래 계획보다 깊은 버그를 하나 더 발견하고 고쳤다**: "조건부 UPDATE 실패 시 insert로 새 행을 만들고, insert가 UNIQUE 위반으로 실패하면 같은 트랜잭션에서 다시 조건부 UPDATE로 재시도"하는 1차 수정은 **Hibernate가 제약 위반 이후 세션을 더 이상 정상적으로 쓸 수 없는 상태로 만들어, 재시도한 UPDATE가 조용히 롤백**되는 것을 실측으로 발견했다(§4.1 — `expected:<15000> but was:<13000>`). 최종적으로는 `INSERT ... ON DUPLICATE KEY UPDATE` 네이티브 UPSERT 한 문장으로 바꿔 근본적으로 해결했다.
4. **위험도 중 하나(회원가입 loginId 중복)는 고치지 않고 현재 동작을 테스트로 고정**했다 — 데이터 정합성(중복 행)은 이미 안전하지만 에러 코드 품질(500 vs 409)은 범위 밖으로 남겼다.
5. **전체 테스트는 120개로 늘어 전부 통과**한다(기존 112개 + 이번에 추가한 8개). 새 동시성 테스트 4개 클래스(8개 테스트)는 총 약 1.5초 안에 끝난다. (코드 리뷰 반영 후 124개로 늘었다 — §7, §12)

---

## 2. 리스트업 — 기능별 동시성 위험도

| # | 기능 | 경합 시나리오 | 현재(수정 전) 방어 | 위험도 | 근거 |
|---|---|---|---|:---:|---|
| 1 | 좌석 예약 | 같은 좌석 N명 동시 요청 | 분산락(`schedule:{id}:seat-reservation`) + 조건부 UPDATE(`holdAvailableSeats`) | — | **완료** (필수 ③, `ConcurrentSeatReservationIntegrationTest`) |
| 2 | 결제 확정 vs 만료 해제 | 결제 확정과 홀드 만료 스케줄러가 동시에 같은 좌석을 건드림 | 조건부 UPDATE(`confirmHeldSeats`/`releaseHeldSeats`) + Redis Lua 원자 정리 | — | **완료** (`2026-10-20-g-fix-review.md` 재리뷰 APPROVE) |
| 3 | **포인트 충전** | 같은 회원이 동시에 N번 충전 | 없음(`find → 계산 → save()`, 분산락 없음) | **상** | 돈(잔액) 직접 영향, lost update는 사용자가 "충전했는데 안 늘었다"로 바로 체감. §4.1에서 실제로 **중복 행 생성**까지 재현됨 |
| 4 | **포인트 차감(결제)** | 같은 회원의 **다른 예약 2건**을 동시에 결제 | 분산락이 **예약 단위**(`reservation:{id}:payment`)라 다른 예약끼리는 보호 안 됨 | **상** | 돈(잔액) 직접 영향. 두 결제가 같은 포인트 행을 두고 경합 |
| 5 | **결제 멱등성** | 같은 예약을 동시에 여러 번 결제 | 분산락(`reservation:{id}:payment`, 커밋 후 해제) + `findByReservationId` 중복 검사, **DB UNIQUE 없음** | **상** | 결제 중복 생성 = 환불 처리 필요한 장애. 락의 leaseTime(3초) 고정이라는 구조적 한계가 이미 handoff에 기록돼 있었음 |
| 6 | **대기열 토큰 발급 정원** | 정원 바로 아래에서 여러 명이 동시 발급 시도 | `countActive` 조회 후 `saveActive` 호출(check-then-act, Redis 비원자) | **상** | 정원이 서비스의 핵심 약속(서버 보호용 트래픽 제한)인데, 몰리는 시점에 가장 취약함 |
| 7 | 대기열 토큰 발급 중복(같은 회원) | 같은 회원이 토큰을 동시에 두 번 요청(재시도, 멀티 탭) | 위 6과 같은 원자화로 **부수적으로 해결됨** | — | §4.3에서 Lua EVAL의 직렬 실행 특성으로 같이 닫힘(별도 수정 불필요) |
| 8 | 회원가입 loginId 중복 | 같은 loginId로 동시 가입 | `findByLoginId` 중복 검사(check-then-act) + DB `UNIQUE` 제약(데이터 정합성은 안전) | **중** | 중복 **행**은 생기지 않음(UNIQUE가 막음). 다만 패자가 받는 예외가 도메인 예외가 아니라 번역된 `DataIntegrityViolationException`이라 HTTP 500으로 빠짐 — UX 품질 문제, 돈·재고 영향 없음 |
| 9 | 대기열 승격(`PromoteQueueTokenService`) 멀티 인스턴스 경합 | 여러 서버 인스턴스의 스케줄러가 동시에 같은 스케줄을 승격 | 단일 인스턴스에선 `@Scheduled`가 단일 스레드라 안전 | **중** | 현재 배포는 Worker Service 1대(싱글 인스턴스 가정)라 당장은 안전. 멀티 인스턴스로 스케일하면 `availableSlots` 계산이 stale해질 수 있음. 테스트로 실측하지 않음(단일 JVM에서 멀티 인스턴스 경합을 사실적으로 재현하기 어려움) |
| 10 | 대기열 토큰 반납(결제 완료) vs 검증(다른 탭의 좌석 조회) | 결제 완료로 토큰이 막 반납되는 순간, 같은 사용자의 다른 탭이 좌석 조회 요청 | 없음 | **하** | 돈·재고 영향 없음. 최악의 경우 그 순간의 요청 1건이 "토큰 무효"로 거절되고 재발급하면 해결됨 |

> **"상" 3건(포인트, 결제 멱등성, 대기열 정원) 모두 §4에서 실측 RED → 수정 → GREEN을 거쳤다.**
> **"중" 1건(loginId 중복)은 §5에서 현재 동작을 고정하는 테스트만 추가했다.**

---

## 3. 재현 방법론

- 모든 RED 측정은 **실제로 `git stash`로 수정 전 커밋 상태의 프로덕션 코드를 복원**한 뒤 동시성 테스트를 실행해서 얻은 값이다. 추측이나 코드 리딩만으로 쓴 수치는 없다.
- 복원 → 측정 → `git stash pop`으로 복구 → 재측정(GREEN 확인)까지 각 영역(포인트/결제/대기열)마다 반복했다.
- 동시성 테스트는 모두 `CountDownLatch` 기반 출발선 패턴(`runConcurrently`/`runConcurrentlyCollecting`, `src/test/java/kr/hhplus/be/server/support/ConcurrencyTestSupport.kt`)으로 스레드를 동시에 출발시킨다. 기존 `ConcurrentSeatReservationIntegrationTest`의 private 헬퍼를 공용으로 추출해 재사용했다.
- 테스트에는 `@Transactional`을 쓰지 않았다(분산락이 커밋 후 해제되는 구조라 테스트 트랜잭션 롤백 방식과 충돌하기 때문 — 기존 필수 테스트와 동일한 제약).

---

## 4. 위험도 "상" — RED → 수정 → GREEN

### 4.1 포인트 충전/차감 — lost update, 그리고 발견한 또 다른 버그

**재현 테스트**: `src/test/java/kr/hhplus/be/server/concurrency/PointConcurrencyIntegrationTest.kt`

#### (a) 같은 회원이 동시에 15번 충전 (1,000원씩, 기대 합계 15,000원)

| 단계 | 코드 상태 | 실측 결과 |
|---|---|---|
| RED (원본) | `ChargePointService`가 `findPointByMemberId` → `Point.chargeAndGetRemained()`(메모리 계산) → `pointRepository.save()`. 분산락 없음. `point.member_id`에 UNIQUE 없음 | **`org.springframework.dao.IncorrectResultSizeDataAccessException: Query did not return a unique result: 3 results were returned`** — 신규 회원이라 "행 없음"을 **동시에 3개 스레드가 보고 각자 INSERT**, 중복 행 3개 생성 |
| 중간 수정 (1차 시도) | 조건부 UPDATE(`chargePoint`)를 추가하고, 0건이면 `save()`로 새 행 생성 → **UNIQUE(member_id) 위반을 catch해서 같은 트랜잭션에서 조건부 UPDATE로 재시도** | **`expected:<15000> but was:<13000>`** — 중복 행은 더 안 생겼지만, 2번의 충전(2,000원)이 사라짐. 아래 §원인 참고 |
| GREEN (최종) | `INSERT ... ON DUPLICATE KEY UPDATE`(네이티브 UPSERT) **한 문장**으로 "있으면 더하고 없으면 만들기"를 원자화 | **15,000원 정확히 일치** (3회 재실행 모두 통과) |

**중간 수정이 실패한 원인(실측으로 확인)**: Hibernate/JPA는 `save()`가 제약 위반(`DataIntegrityViolationException`)으로 실패하면, 그 예외를 애플리케이션이 catch해서 무시해도 **같은 트랜잭션·같은 EntityManager로는 더 이상 안전하게 flush할 수 없는 상태**가 된다. 그 상태에서 재시도한 조건부 UPDATE(JPQL bulk update)는 겉보기엔 실행되지만, 전체 `@Transactional` 메서드가 커밋을 시도할 때 **조용히 롤백**될 수 있다는 것을 실측으로 확인했다(테스트 코드 자체는 예외 없이 끝났지만 DB에 반영되지 않음). 이 패턴("insert 실패 → catch → 같은 트랜잭션에서 재시도")은 일반적으로 위험하다는 것을 이번에 직접 겪었다 — **최종 해결은 재시도 자체를 없애고 DB 레벨 UPSERT로 바꾸는 것**이었다.

#### (b) 같은 회원의 서로 다른 예약 2건을 동시에 결제 (락 키가 reservation 단위라 서로 보호 안 됨)

| 단계 | 실측 결과 |
|---|---|
| RED 시도 (원본, 3회 반복) | **재현 안 됨** — 3번 모두 정상적으로 두 결제 금액이 모두 차감됨 |
| GREEN (수정 후) | 정상 통과 (조건부 UPDATE가 적용돼 있어 더 안전해짐) |

**재현 안 됨 + 이유**: 2개 스레드만으로는 "포인트 조회 → 검증 → 조건부 UPDATE" 사이의 임계 구간이 타이밍상 거의 겹치지 않았다(반복 3회 모두 재현 안 됨). 다만 이건 **"안전하다"는 뜻이 아니라 "이 테스트 규모로는 못 잡았다"**는 뜻이다 — 분산락이 예약 단위라 두 결제가 **코드상 전혀 직렬화되지 않는다**는 사실(§코드 분석)은 변하지 않으므로, 수정(조건부 UPDATE)은 재현 여부와 무관하게 적용했다.

**수정 내용**:
- `PointRepository`에 `chargePoint(memberId, amount): Int`, `usePoint(memberId, amount): Int` 추가.
- `SpringPointJpa.chargePoint`: `INSERT INTO point (...) VALUES (...) ON DUPLICATE KEY UPDATE point = point + :amount, ...` (네이티브 쿼리).
- `SpringPointJpa.usePoint`: `UPDATE PointEntity p SET p.point = p.point - :amount WHERE p.memberId = :memberId AND p.point >= :amount` (JPQL 조건부 UPDATE, 0건이면 잔액 부족).
- `PointEntity.memberId`에 `@Column(unique = true)` 추가(회원당 포인트 행 1개 보장) — 기존 `idx_member_id` 인덱스는 중복이라 제거(`payment.idx_number`와 같은 패턴, §5 인덱스 보고서).
- `ChargePointService`/`ProcessPaymentService.processPointUsage`는 이제 도메인 모델을 메모리에서 mutate하지 않고 저장소의 원자적 연산에 위임한다. 차감 쪽은 **사전 검증(빠른 실패용) + 조건부 UPDATE(최후 방어선)**를 모두 유지했다 — 사전 검증을 지나고도 조건부 UPDATE가 0건이면 `InsufficientPointException`을 던진다(단위 테스트로 검증, `ProcessPaymentServiceTest` "사전 검증 통과 후 동시 결제로 조건부 UPDATE 시점에 잔액이 부족해졌을 때").

**대안 검토**: 분산락으로 회원 단위(`member:{id}:point`) 락을 추가로 거는 방법도 가능했지만, (1) 결제 흐름에 락이 하나 더 늘어 데드락 순서 관리가 복잡해지고, (2) DB 조건부 UPDATE가 이미 "DB가 최후 방어선"이라는 Step4 원칙과 직접 맞아 더 적합하다고 판단했다.

**새 UNIQUE 제약(`point.member_id`)의 운영 반영 주의** (코드 리뷰 HIGH 반영, `docs/agent/reviews/2026-10-21-concurrency-review.md`): `payment.reservation_id`와 같은 사정이 `point.member_id`에도 그대로 적용된다(§4.2 참고) — prod는 `ddl-auto: update`인데 Hibernate의 `update` 모드는 제약을 **추가는 시도**하지만, 추가 전에 기존 데이터의 중복을 정리해주지는 않는다(`docs/report/step4-index-report.md`에서 실측). 그런데 이번엔 한 가지가 더 있다: **이 PR이 고치는 버그 자체가 "동시 최초 충전 시 포인트 중복 행 생성"**이므로(§4.1 RED 단계에서 `IncorrectResultSizeDataAccessException`으로 실측), 운영 DB에는 수정 전 버그로 인해 `member_id`가 중복된 행이 이미 쌓여 있을 가능성이 있다(실측이 아니라 추정 — 운영 DB 접근 권한이 없어 직접 확인하지 못했다). 이 상태에서 제약을 배포하면 제약 생성 자체가 **실패**한다.

- **배포 전 점검 쿼리**(payment 쪽엔 없던, 이번에 추가하는 절차):
  ```sql
  SELECT member_id, COUNT(*) FROM point GROUP BY member_id HAVING COUNT(*) > 1;
  ```
  1건이라도 나오면 제약 배포를 보류하고 아래 정리가 선행되어야 한다.
- **정리 절차(제안 — 실제 실행은 운영자 판단)**:
  1. 중복된 `member_id`별로, 그 회원의 `point_history`(`user_id`로 연결, `point.id`가 아니라 `point.member_id`와 매칭되는 회원 단위 이력) 합계와 각 중복 행의 `point` 값을 대조해 데이터가 이미 깨져 있는지 먼저 판별한다.
  2. 잔액을 합산해 1행(예: 가장 먼저 생성된 행)으로 병합하고 나머지 중복 행은 삭제한다. `docs/project/erd.md` 기준 `point_history`는 `member_id`(ERD 표기상 `user_id`)로만 회원과 연결되고 `point.id`를 참조하는 FK가 없으므로, 포인트 행을 병합·삭제해도 이력 조회에는 영향이 없다(ERD 확인, 실제 FK 제약은 미실측).
  3. 병합 후 점검 쿼리로 중복 0건을 재확인한 뒤에만 제약을 배포한다.
- 이번 세션에서는 로컬 테스트 스키마(`create-drop`, Testcontainers MySQL 8.0)로 UNIQUE 동작 자체만 확인했고, 운영 DB의 실제 중복 여부는 조사하지 않았다(§11 한계에도 기록).

---

### 4.2 결제 멱등성 — 락은 이미 보호하지만, DB 단독 방어선은 없었다

**재현 테스트**: `src/test/java/kr/hhplus/be/server/concurrency/PaymentIdempotencyConcurrencyIntegrationTest.kt` (테스트 3개)

| 테스트 | 경로 | RED(원본) | GREEN(수정 후) |
|---|---|---|---|
| 같은 예약을 분산락 경유로 동시에 N번 결제 | Facade 전체 흐름(락 포함) | **재현 안 됨** — 1번 성공 + 나머지 `DuplicatePaymentException` (정상) | 동일 (변화 없음, 락이 원래도 보호) |
| 락 없이 저장소를 **순차** 2번 호출 | `paymentRepository.save()` 직접 | **`Expected exception DuplicatePaymentException but no exception was thrown.`** — 2번째 저장도 그냥 성공 | `DuplicatePaymentException` 발생 |
| 락 없이 저장소를 **10개 트랜잭션 동시** 호출 | `paymentRepository.save()` 직접 × 10 | **`expected:<1> but was:<10>`** — 10개 모두 성공, 같은 reservation_id로 결제 10건 생성 | 정확히 1건 성공, 나머지 9건 `DuplicatePaymentException` |

**락 테스트가 "재현 안 됨"인 이유(실측으로 확인)**: `LockManager.runWithLock`은 `DistributedLockTransactionProcessor.proceed()`(`@Transactional(REQUIRES_NEW)`)를 감싸고, 락 해제(`rLock.unlock()`)는 그 트랜잭션이 **커밋된 뒤**(메서드가 리턴한 뒤 `finally`)에 일어난다. 즉 같은 reservation에 대한 동시 결제 요청은 **완전히 직렬화**되고, 두 번째 요청이 락을 잡는 시점엔 첫 번째 요청의 결제 행이 이미 커밋돼 있어 `findByReservationId` 중복 검사가 정상 작동한다. 이걸 실측으로 재확인했다(수정 전/후 모두 1성공 + N-1 `DuplicatePaymentException`).

**그럼에도 DB UNIQUE를 추가한 이유**: 락의 `leaseTime`이 3초로 **고정값**이고(`docs/agent/handoff.md`에 이미 구조적 위험으로 기록돼 있던 지적), 네트워크 지연이나 GC pause 등으로 결제 처리가 3초를 넘기면 Redisson이 자동으로 락을 풀어 다른 요청이 끼어들 수 있다 — 이 경로는 현재 테스트로 강제 재현하기 어렵지만(타이밍 의존적), **락이 뚫렸을 때 DB가 막아줘야** "DB가 마지막 방어선"이라는 원칙에 맞는다. 실제로 **락을 거치지 않고 저장소를 직접 두드린 테스트**가 이 가정을 실측으로 증명한다 — 원본 코드는 중복 결제 10건을 모두 허용했다.

**수정 내용**:
- `PaymentEntity`의 `@Table`에 이름 있는 `uniqueConstraints = [UniqueConstraint(name = "uk_payment_reservation_id", columnNames = ["reservation_id"])]`를 추가(예약당 결제 1건 보장). 기존 `@Table(indexes=[idx_reservation_id])`는 제거(`payment.idx_number`와 같은 패턴).
- `PaymentJpaRepository.save()`에서 `DataIntegrityViolationException`을 `DuplicatePaymentException`으로 변환. 기존 앱 레벨 중복 검사(`findByReservationId`)는 그대로 유지(빠른 실패용).

**코드 리뷰 MEDIUM 반영 — 예외 변환 범위 좁히기** (`docs/agent/reviews/2026-10-21-concurrency-review.md`): 처음엔 `@JoinColumn(unique = true)`만 썼는데, 이렇게 하면 Hibernate가 제약 이름을 `UKxxxxxx` 해시값으로 임의 생성해 "이 예외가 정말 reservation_id 중복 때문인지"를 코드에서 식별할 수 없고, `PaymentJpaRepository.save()`는 FK 위반·NOT NULL 위반 등 **모든** `DataIntegrityViolationException`을 뭉뚱그려 `DuplicatePaymentException`으로 바꿔버리는 문제가 있었다. 그래서:
1. `@Table(uniqueConstraints = [...])`로 제약 이름을 `uk_payment_reservation_id`로 직접 고정하고, `@JoinColumn`의 `unique = true`는 같은 제약이 중복 생성되는 것을 막기 위해 제거했다.
2. `PaymentJpaRepository.save()`는 `DataIntegrityViolationException`의 원인(`cause`)이 `org.hibernate.exception.ConstraintViolationException`이고 그 `constraintName`에 `uk_payment_reservation_id`가 포함될 때만 `DuplicatePaymentException`으로 변환하고, 그 외(다른 제약 위반 등)는 그대로 전파해 원인을 숨기지 않는다.

**실측(Testcontainers MySQL 8.0, 중복 저장 시도로 직접 유도)**:
- `DataIntegrityViolationException.message` = `could not execute statement [Duplicate entry '1' for key 'payment.uk_payment_reservation_id'] [...]; constraint [payment.uk_payment_reservation_id]`
- `cause`의 클래스 = `org.hibernate.exception.ConstraintViolationException`, `cause.mostSpecificCause`(JDBC) 클래스 = `java.sql.SQLIntegrityConstraintViolationException`, 메시지 = `Duplicate entry '1' for key 'payment.uk_payment_reservation_id'`
- `ConstraintViolationException.constraintName` = `"payment.uk_payment_reservation_id"` — MySQL 8이 "테이블명.제약이름" 형태로 돌려준다는 것을 실측으로 확인했다. 이 접두사 포맷이 MySQL 버전/설정에 따라 달라질 수 있어, 정확히 일치(`==`)가 아니라 제약 이름이 **포함**되는지(`contains`)로 판단하도록 구현했다.

**새 UNIQUE 제약의 운영 반영 주의**: `docs/agent/handoff.md`와 `docs/report/step4-index-report.md`에 이미 기록된 대로, prod는 `ddl-auto: update`인데 Hibernate의 `update` 모드는 **제약 추가는 하지만 기존 데이터의 중복을 검사하며 제약을 걸 수는 없다** — 만약 운영 DB에 이미 같은 `reservation_id`로 결제가 2건 이상 있다면 이 제약 자체가 생성에 **실패**한다(수동 마이그레이션 전 데이터 정리 필요). 이번 세션에서는 로컬 테스트 스키마(`create-drop`)로만 확인했고, 운영 데이터 중복 여부는 확인하지 않았다.

---

### 4.3 대기열 토큰 발급 정원 — check-then-act를 Redis Lua로 원자화

**재현 테스트**: `src/test/java/kr/hhplus/be/server/concurrency/QueueCapacityConcurrencyIntegrationTest.kt` (테스트 2개)

| 테스트 | RED(원본: `countActive` 후 `saveActive`) | GREEN(수정 후: `tryIssueActive` Lua) |
|---|---|---|
| 정원 5명, 신규 사용자 20명 동시 발급 시도 | **`expected:<5> but was:<18>`** — 20명 중 18명이 발급됨(정원을 13명 초과) | 정확히 5명만 발급, `countActive <= 5` |
| 같은 사용자가 동시에 10번 발급 요청(재시도·멀티 탭) | **`expected:<1L> but was:<10L>`** — 활성 인원이 10명으로 집계(사실은 1명) | 활성 인원 정확히 1명 |

**수정 내용**: `ActiveQueueRepository.tryIssueActive(scheduleId, uuid, capacity): Boolean`을 추가하고, Redis Lua 스크립트(`TRY_ISSUE_ACTIVE_SCRIPT`)로 "이미 유효한 활성 토큰이 있으면 유지, 없고 정원 미만이면 등록"을 **하나의 EVAL**로 묶었다. Redis는 EVAL을 한 번에 하나씩만 실행하므로, 여러 스레드가 동시에 호출해도 "확인 후 등록" 사이에 다른 호출이 끼어들 수 없다.

- `IssueQueueTokenService`는 `AdmissionPolicy`/`countActive`+`saveActive` 대신 `tryIssueActive`를 쓰도록 변경했고, 더 이상 쓰이지 않게 된 `AdmissionPolicy`/`ThresholdAdmissionPolicy`는 삭제했다(`PromoteQueueTokenService`는 원래부터 이 정책을 쓰지 않았고 `capacity`를 직접 주입받는 자기만의 패턴이라 영향 없음).
- Lua의 KEYS는 `RedisTemplate`의 keySerializer(`StringRedisSerializer`)로, ARGV/반환값은 valueSerializer(`GenericJackson2JsonRedisSerializer`)로 직렬화된다는 것을 이미 있던 `SeatHoldRedisRepository`의 Lua 스크립트 패턴을 그대로 따라 재확인했다(바이트코드로 `DefaultScriptExecutor`를 직접 확인). 반환값은 Long(0/1)만 쓰도록 설계해 JSON 직렬화 함정을 피했다.

**부수적으로 같이 닫힌 위험**: 리스트업 표의 #7(같은 회원 동시 발급 시 토큰 중복)도 같은 수정으로 막힌다 — 같은 uuid로 동시에 호출된 두 `tryIssueActive`는 Redis 입장에서 **순서대로 실행되는 두 개의 원자적 트랜잭션**이라, 두 번째 호출 시점에는 첫 번째 호출이 이미 심어둔 uuid→token 매핑이 보이고 그대로 재사용된다. 별도 수정 없이 테스트로 증명했다(위 표의 두 번째 행).

---

## 5. 위험도 "중" — 현재 동작을 테스트로 고정 (수정하지 않음)

### 회원가입 loginId 중복

**고정 테스트**: `src/test/java/kr/hhplus/be/server/concurrency/MemberLoginIdConcurrencyIntegrationTest.kt`

같은 loginId로 10개 트랜잭션이 동시에 가입을 시도하면:
- **데이터 정합성은 안전**하다 — `member.login_id`의 `UNIQUE` 제약 덕분에 정확히 1건만 성공하고 DB에 중복 행이 생기지 않는다(실측 확인, 반복 2회).
- 다만 패자 9건은 `LoginIdDuplicateException`(409)이 아니라 **번역된 `DataIntegrityViolationException`을 그대로** 받는다 — `MemberRepository`가 `JpaRepository`를 직접 상속하는 통합형 구조(`docs/report/step4-index-report.md`에서 "현상 유지"로 이미 결정된 구조)라 제약 위반을 도메인 예외로 변환하는 지점이 없기 때문이다. `GlobalExceptionHandler`의 범용 `Exception` 핸들러로 떨어져 HTTP 500이 된다.

**고치지 않은 이유**: 돈·재고에 영향이 없고(중복 회원이 생기지 않음), 에러 코드 품질(500 vs 409) 문제라 이번 선택 심화 ②의 "상" 기준(돈·재고·정원)에 해당하지 않는다. 고치려면 `MemberAuthService.signUp()` 또는 `MemberRepository` 구현체에서 `DataIntegrityViolationException → LoginIdDuplicateException` 변환을 추가하면 된다(결제 멱등성 수정과 동일한 패턴).

### 그 외 "중/하" 항목 (테스트 없이 리스트업만, §2 표 참고)

- **대기열 승격 멀티 인스턴스 경합**(중): 단일 인스턴스에선 `@Scheduled`가 단일 스레드라 안전. 멀티 인스턴스 재현은 단일 JVM 테스트로는 사실적으로 만들기 어려워 테스트를 작성하지 않았다. 대안: `tryIssueActive`와 같은 Lua 원자화를 승격 경로에도 적용하거나, 스케줄러 자체에 분산락을 거는 방법.
- **대기열 토큰 반납 vs 검증 경합**(하): 돈·재고 영향 없음, 최악의 경우 요청 1건의 일시적 거절. 테스트 작성하지 않음.

---

## 6. Rule 목록 — "이 기능은 이 테스트가 지킨다"

| Rule | 지키는 테스트 |
|---|---|
| 포인트 잔액 변경은 항상 DB 조건부 UPDATE/UPSERT(`chargePoint`/`usePoint`)로만 이뤄지고, 동시 충전·동시 결제가 서로의 변경을 덮어쓰지 않는다 | `PointConcurrencyIntegrationTest` (2개) |
| 결제는 예약 하나당 정확히 하나만 존재한다 — 분산락이 뚫리더라도 DB UNIQUE 제약이 막는다 | `PaymentIdempotencyConcurrencyIntegrationTest` (3개) |
| 대기열 활성 인원은 동시 발급 요청이 몰려도 정원을 넘지 않는다. 같은 사용자의 중복 요청은 활성 인원에 1명으로만 집계된다 | `QueueCapacityConcurrencyIntegrationTest` (2개) |
| 같은 loginId로 동시 가입해도 회원 행은 정확히 1개만 생성된다(에러 코드는 미정 — 고정만 함) | `MemberLoginIdConcurrencyIntegrationTest` (1개) |
| (기존) 같은 좌석에 동시 예약 요청이 몰려도 한 명만 성공한다 | `ConcurrentSeatReservationIntegrationTest` (필수 ③, 변경 없음) |
| 포인트 `chargePoint`/`usePoint`는 단일 스레드에서도 각자 명시된 대로 동작한다(신규 생성/기존 증가/정상 차감/잔액 부족 시 0건·불변) — 코드 리뷰 반영(LOW) | `PointRepositoryIntegrationTest` (4개) |

---

## 7. `./gradlew test` 결과

- **전체 124개 테스트, 0 failures, 0 errors** (`./gradlew clean test`, 약 50초).
  - 기존 112개(필수 ①②③ + 선택 심화 ① 포함) + 동시성 테스트 8개(포인트 2 + 결제 멱등성 3 + 대기열 정원 2 + loginId 1) + 코드 리뷰 반영으로 추가한 포인트 저장소 통합 테스트 4개(`PointRepositoryIntegrationTest` — chargePoint 신규/기존, usePoint 정상/잔액부족).
- 새 동시성 테스트 4개 클래스(8개 테스트)만의 소요 시간: **약 1.5초**(0.21s + 0.57s + 0.42s + 0.26s, 클래스별 Testcontainers 재사용으로 매우 빠름).
- 기존 `ConcurrentSeatReservationIntegrationTest`(필수 ③)는 약 10.3초로 변동 없음(private 헬퍼를 공용 `runConcurrently`로 추출했을 뿐 로직은 바꾸지 않음).
- `perfTest`에는 코드 리뷰 MEDIUM 항목 재현 시도용 `PointUpsertDeadlockReproductionTest`(`@Tag("perf")`)가 추가됐다 — 스레드 수가 크고(30~50) 타이밍에 의존적이라 기본 `./gradlew test`에는 포함하지 않았다(§11 한계에 결과 기록). `./gradlew perfTest --tests "...PointUpsertDeadlockReproductionTest"`로 약 20~30초 안에 실행된다.

---

## 8. 변경 파일 목록

### 프로덕션 코드
- `src/main/kotlin/kr/hhplus/be/server/domains/point/domain/repository/PointRepository.kt` — `chargePoint`/`usePoint` 추가
- `src/main/kotlin/kr/hhplus/be/server/domains/point/infrastructure/persistence/SpringPointJpa.kt` — UPSERT 네이티브 쿼리, 조건부 UPDATE
- `src/main/kotlin/kr/hhplus/be/server/domains/point/infrastructure/persistence/PointJpaRepository.kt` — 위 메서드 위임
- `src/main/kotlin/kr/hhplus/be/server/domains/point/infrastructure/persistence/PointEntity.kt` — `memberId` UNIQUE, 중복 인덱스 제거
- `src/main/kotlin/kr/hhplus/be/server/domains/point/application/usecase/ChargePointService.kt` — 원자적 충전으로 전환
- `src/main/kotlin/kr/hhplus/be/server/domains/payment/application/usecase/ProcessPaymentService.kt` — `processPointUsage`를 원자적 차감으로 전환
- `src/main/kotlin/kr/hhplus/be/server/domains/payment/infrastructure/persistence/PaymentEntity.kt` — `reservation_id` UNIQUE, 중복 인덱스 제거
- `src/main/kotlin/kr/hhplus/be/server/domains/payment/infrastructure/persistence/PaymentJpaRepository.kt` — 제약 위반 → `DuplicatePaymentException` 변환
- `src/main/kotlin/kr/hhplus/be/server/domains/queue/domain/repository/ActiveQueueRepository.kt` — `tryIssueActive` 추가
- `src/main/kotlin/kr/hhplus/be/server/domains/queue/infrastructure/redis/ActiveQueueRedisRepository.kt` — Lua `TRY_ISSUE_ACTIVE_SCRIPT` 구현
- `src/main/kotlin/kr/hhplus/be/server/domains/queue/application/usecase/IssueQueueTokenService.kt` — `tryIssueActive` 사용으로 전환
- `src/main/kotlin/kr/hhplus/be/server/domains/queue/infrastructure/config/QueueUseCaseConfig.kt` — 정책 빈 제거, capacity 직접 주입
- `src/main/kotlin/kr/hhplus/be/server/domains/queue/domain/policy/AdmissionPolicy.kt`, `ThresholdAdmissionPolicy.kt` — **삭제** (더 이상 쓰이지 않는 코드)

### 테스트 코드
- `src/test/java/kr/hhplus/be/server/concurrency/PointConcurrencyIntegrationTest.kt` (신규)
- `src/test/java/kr/hhplus/be/server/concurrency/PaymentIdempotencyConcurrencyIntegrationTest.kt` (신규)
- `src/test/java/kr/hhplus/be/server/concurrency/QueueCapacityConcurrencyIntegrationTest.kt` (신규)
- `src/test/java/kr/hhplus/be/server/concurrency/MemberLoginIdConcurrencyIntegrationTest.kt` (신규)
- `src/test/java/kr/hhplus/be/server/support/ConcurrencyTestSupport.kt` (신규) — `runConcurrently`/`runConcurrentlyCollecting` 공용 헬퍼
- `src/test/java/kr/hhplus/be/server/integration/ConcurrentSeatReservationIntegrationTest.kt` — private 헬퍼를 공용 헬퍼 사용으로 교체(로직 변경 없음)
- `src/test/java/kr/hhplus/be/server/IntegrationTestConfiguration.kt`, `support/fixture/ConcertReservationFixture.kt` — 포인트 충전 픽스처(`chargePoint`) 추가
- `src/test/java/kr/hhplus/be/server/domains/point/application/usecase/ChargePointServiceTest.kt`, `domains/payment/application/usecase/ProcessPaymentServiceTest.kt`, `domains/queue/application/usecase/IssueQueueTokenServiceTest.kt` — 변경된 프로덕션 코드에 맞춰 단위 테스트 갱신

### 코드 리뷰 반영 (2026-10-21, `docs/agent/reviews/2026-10-21-concurrency-review.md`)
- `src/main/kotlin/kr/hhplus/be/server/domains/payment/infrastructure/persistence/PaymentEntity.kt` — `@JoinColumn(unique=true)` 대신 이름 있는 `@Table(uniqueConstraints=[uk_payment_reservation_id])`로 변경(MEDIUM)
- `src/main/kotlin/kr/hhplus/be/server/domains/payment/infrastructure/persistence/PaymentJpaRepository.kt` — `DataIntegrityViolationException` → `DuplicatePaymentException` 변환을 `uk_payment_reservation_id` 제약 위반일 때만으로 좁힘(MEDIUM)
- `src/main/kotlin/kr/hhplus/be/server/domains/point/infrastructure/persistence/SpringPointJpa.kt` — `usePoint`가 `updated_at`도 함께 갱신하도록 수정(LOW). 처음엔 JPQL `CURRENT_TIMESTAMP`로 했다가 `now(6)`(마이크로초)와 정밀도가 달라(초 단위로 잘림) 문제가 될 수 있음을 테스트로 발견하고, `chargePoint`와 동일한 네이티브 `now(6)` 쿼리로 변경
- `src/main/kotlin/kr/hhplus/be/server/domains/point/domain/repository/PointRepository.kt` — `chargePoint`/`usePoint` KDoc에 MySQL의 영향받은 행 수(1/2) 동작 기록
- `src/test/java/kr/hhplus/be/server/domains/point/infrastructure/persistence/PointRepositoryIntegrationTest.kt` (신규) — `chargePoint`(신규 행/기존 행 증가), `usePoint`(정상 차감/잔액 부족) 단일 스레드 검증 4건(LOW)
- `src/test/java/kr/hhplus/be/server/concurrency/PointUpsertDeadlockReproductionTest.kt` (신규, `@Tag("perf")`) — UPSERT 데드락 재현 시도(결과: 재현 안 됨, §11 한계)(MEDIUM)

### 커넥션 풀 크기 결정 반영 (2026-10-21, §10-1)
- `src/main/resources/application.yml` — `spring.datasource.hikari.maximum-pool-size: 3 → 15`
- `src/test/java/kr/hhplus/be/server/concurrency/PaymentIdempotencyConcurrencyIntegrationTest.kt` — `ATTEMPT_COUNT: 2 → 10`(원래 의도한 동시 결제 수준으로 복원), 관련 주석 갱신

### 문서
- `docs/report/step4-concurrency-report.md` (이 문서)

---

## 9. 면접 포인트 3개

1. **"원자적 조건부 UPDATE만으로는 부족할 수 있다"**: 포인트 최초 충전 경합을 고치면서, "조건부 UPDATE 실패 → insert로 새 행 생성 → insert가 UNIQUE 위반이면 같은 트랜잭션에서 조건부 UPDATE로 재시도"라는 1차 수정이 실제로 `expected:<15000> but was:<13000>`이라는 lost update를 만든 것을 실측으로 발견했다. 원인은 Hibernate가 제약 위반(flush 실패) 이후 같은 세션/트랜잭션을 더 이상 안전하게 쓸 수 없는 상태로 만든다는 것 — 애플리케이션이 예외를 catch해도 DB 트랜잭션 레벨에서는 이미 문제가 생긴 뒤였다. 최종적으로는 재시도 자체를 없애고 `INSERT ... ON DUPLICATE KEY UPDATE` 네이티브 UPSERT 한 문장으로 해결했다. **"고쳤다고 생각한 코드를 실제로 동시성 테스트로 다시 돌려봐야 하는 이유"**를 보여주는 사례다.
2. **"분산락이 있어도 DB 제약이 필요한 이유"**: 결제 멱등성에서, 분산락(커밋 후 해제)이 **이미 완전히 직렬화**하고 있어 정상 시나리오에서는 중복이 재현되지 않는다는 것을 실측으로 확인했다. 그럼에도 `leaseTime`이 3초 고정값이라는 구조적 한계 때문에 DB UNIQUE 제약을 추가로 걸었고, **락을 거치지 않고 저장소를 직접 두드린 테스트**로 "락이 없으면 원본 코드가 중복 결제 10건을 전부 허용한다"는 걸 증명했다. 애플리케이션 락은 네트워크 지연·GC pause 등 가정이 깨질 수 있어, DB 제약이 진짜 "마지막 방어선"이라는 Step4 원칙을 결제 도메인에도 일관되게 적용한 사례.
3. **Redis Lua EVAL의 원자성으로 check-then-act를 닫는 패턴과 그 부수 효과**: 대기열 정원 체크(`countActive`)와 등록(`saveActive`)을 한 EVAL로 묶으면, Redis가 명령을 하나씩만 순서대로 실행한다는 성질 덕분에 "정원 초과 발급"뿐 아니라 "같은 사용자 동시 중복 발급"까지 **같은 수정으로 부수적으로 막힌다**는 걸 테스트로 증명했다(두 테스트가 서로 다른 위험을 검증하지만 수정은 하나). 또한 Spring Data Redis가 Lua의 KEYS는 keySerializer로, ARGV/반환값은 valueSerializer로 **다르게 직렬화**한다는 점(바이트코드로 직접 확인)을 이해하고 반영했다 — 반환값을 String으로 설계했다면 JSON 직렬화 경계에서 또 다른 함정에 빠질 뻔했다.

---

## 10. 판단을 미룬 것 / 사용자 결정이 필요한 것

1. **(결정 완료, 2026-10-21) HikariCP 커넥션 풀 크기: 3 → 15**

   **문제**: 분산락을 거쳐 전체 결제 플로우를 테스트할 때 동시 스레드 수를 올리면 `RuntimeException: Failed to acquire lock`이 발생한다(분산락 `waitTime` 5초 초과). 그래서 이전 세션에서는 `PaymentIdempotencyConcurrencyIntegrationTest`의 `ATTEMPT_COUNT`를 2로 낮춰 임시로 안정화해뒀다.

   **원인(코드로 확정, 추정 아님)**: `PaymentFacade.processPayment`는 `@Transactional`(바깥)이고, 그 안의 `lockManager.runWithLock`이 `DistributedLockTransactionProcessor.proceed`(`@Transactional(REQUIRES_NEW)`, 안쪽)를 감싼다. Hibernate는 커넥션을 지연 획득하므로, 바깥 트랜잭션이 실제로 커넥션을 쥐는 시점은 메서드 진입 직후가 아니라 **첫 쿼리가 실행되는 시점**이다 — 그 첫 쿼리가 `PaymentFacade.processPayment` 맨 앞의 `authenticatedMemberReader.resolveMemberId(uuid)`(→ `MemberAuthenticatedMemberReader.resolveMemberId` → `memberRepository.findByUuid`)이고, 이 호출은 분산락을 시도하기 **전에** 일어난다. 즉 같은 예약에 N명이 동시에 결제를 시도하면:
   - N개 스레드 **모두** 바깥 트랜잭션용 커넥션을 먼저 쥔 채로 Redis 락(`rLock.tryLock`)을 기다린다(DB가 아니라 Redis에서 경합하므로 서로의 커넥션을 막지 않음).
   - 그중 락을 쥔 **1명만** `DistributedLockTransactionProcessor.proceed`(`REQUIRES_NEW`)로 **같은 풀에서** 커넥션을 1개 더 빌려야 실제 비즈니스 로직(검증·좌석 확정·포인트 차감·결제 저장)을 실행할 수 있다.
   - 따라서 동시 N건이 몰리는 최악의 경우 필요한 커넥션 수는 N이 아니라 **N+1**이다 — 풀 크기가 N과 같거나 작으면, 락을 쥔 1명이 11번째 커넥션을 못 받아 `connection-timeout`(10초)에 걸리고, 나머지 N-1명은 `waitTime`(5초)에 걸려 `Failed to acquire lock`으로 실패한다. **스레드 수(동시 요청 수)가 풀 크기를 넘거나 같으면 "락 대기"와 "커넥션 대기"가 같은 타이밍에 겹쳐 전체가 타임아웃난다**는 뜻이다.

   **인프라 근거 조사** (`infra/terraform/`):
   | 값 | 조사 결과 | 출처 |
   |---|---|---|
   | RDS 인스턴스 클래스 | `db.t3.micro` | `infra/terraform/envs/prod/terraform.tfvars`(`db_instance_class`), `infra/terraform/modules/datastore/variables.tf`(default) |
   | RDS 커스텀 파라미터 그룹 | 없음 — `aws_db_parameter_group` 리소스가 terraform에 없고, `aws rds describe-db-parameter-groups`로도 `default.mysql8.0` 하나만 존재 확인 | terraform 전체 grep + AWS CLI(`--profile ys-admin`) 실측 |
   | `max_connections` 파라미터 값 | 수식 `{DBInstanceClassMemory/12582880}` (AWS 기본값, 수정 없음) — `aws rds describe-db-parameters --db-parameter-group-name default.mysql8.0`로 ParameterValue 자체는 실측했지만, **이 수식이 실제로 계산되는 숫자(`max_connections`의 최종 값)는 미확인**이다. 현재 RDS 인스턴스와 ECS 클러스터가 모두 중단/삭제된 상태(`aws rds describe-db-instances` → 빈 배열, `aws ecs list-clusters` → 빈 배열)라 `SHOW VARIABLES LIKE 'max_connections'`나 콘솔의 effective 값을 조회할 대상 자체가 없었다. db.t3.micro의 원시 메모리는 1024MiB(`aws ec2 describe-instance-types --instance-types t3.micro`로 실측)이지만, RDS가 수식에 쓰는 `DBInstanceClassMemory`는 원시 메모리보다 작은 내부 상수라 1024MiB로 역산한 상한(≈85)은 과대추정일 수 있다. AWS 공개 자료에서 흔히 인용되는 db.t3.micro 기본값(약 66)은 **이번 세션에서 실측하지 않은 참고치**로만 쓴다 | AWS CLI 실측 + 미확인 사실 명시 |
   | ECS API 태스크 수 | `api_desired_count = 1` (오토스케일링 없음) | `infra/terraform/envs/prod/terraform.tfvars`, `infra/terraform/modules/ecs-service/variables.tf` |
   | ECS Worker 태스크 수 | `worker_desired_count = 1` (오토스케일링 없음) | 위와 동일 |
   | 오토스케일링(`aws_appautoscaling_target` 등) | terraform 어디에도 없음 → desired_count가 곧 현재 인프라의 **최대치** | terraform 전체 grep으로 확인 |
   | Bastion | 1대(`infra/terraform/modules/bastion`), 운영자가 수동 SSH 접속하는 용도라 상시 커넥션을 차지하지 않음 → 여유치에 포함 | `infra/terraform/main.tf`의 `module "bastion"` |

   **계산**:
   - 상한: `인스턴스 수(API 1 + Worker 1 = 2) × maximum-pool-size + 여유(수동 접속·마이그레이션 툴 등, 10) ≤ max_connections`. 풀 크기 15를 넣으면 `2×15+10=40`. `max_connections`의 정확한 값은 미확인이지만, 1024MiB 전체를 기준으로 역산한 상한(≈85)이나 공개 참고치(≈66) 어느 쪽과 비교해도 40은 충분히 여유롭다.
   - 최소 필요치(요청 1건당 최대 커넥션 2개 반영): 동시 결제 목표 N=10(`PaymentIdempotencyConcurrencyIntegrationTest`의 `ATTEMPT_COUNT` 기준) → 위 "N+1" 분석에 따라 **최소 11**.
   - 결정: **15** (최소 필요치 11에 여유 4를 더함 — 테스트 타이밍 변동, 운영 중 다른 흐름의 동시 접속까지 고려한 보수적 여유). `minimum-idle`은 별도로 설정하지 않았다(HikariCP 기본값이 `maximum-pool-size`와 동일하게 유지되고, 이 프로젝트 규모에서 유휴 커넥션을 더 줄일 필요가 없어 "최소 변경" 원칙에 따라 그대로 둠).

   **실측(RED → GREEN, `PaymentIdempotencyConcurrencyIntegrationTest`의 "같은 예약을 동시에 여러 번 결제" 테스트만, `ATTEMPT_COUNT`를 2→10으로 되돌린 상태에서 매번 `--rerun`으로 재실행)**:

   | 풀 크기 | 스레드 수(N) | 결과 |
   |---|---|---|
   | 3 (기존값) | 10 | **RED** — `java.lang.RuntimeException: Failed to acquire lock`로 1개 테스트 실패 (`AssertionError: Collection should be empty but contained ...`) |
   | 10 (= N, 기본 제안값) | 10 | **RED** — 동일하게 재현됨. 풀 크기가 동시 스레드 수와 같아도 부족하다는 것을 실측으로 확인(위 "N+1" 계산과 일치) |
   | 11 (= N+1, 이론적 최소) | 10 | **GREEN** — `BUILD SUCCESSFUL` |
   | 15 (최종 결정값) | 10 | **GREEN** — `BUILD SUCCESSFUL`, 이어서 `./gradlew clean test` 전체 124개 테스트 0 failures로 재확인 |

   **변경 파일**: `src/main/resources/application.yml`(`maximum-pool-size: 3 → 15`), `src/test/java/kr/hhplus/be/server/concurrency/PaymentIdempotencyConcurrencyIntegrationTest.kt`(`ATTEMPT_COUNT: 2 → 10`, 주석도 갱신).

   **후속 검토(근본 대안, 이번엔 범위 밖으로 남김)**: 가장 근본적인 해결은 `PaymentFacade.processPayment`의 바깥 `@Transactional`을 없애거나(혹은 분산락 바깥에서 커넥션을 쥐지 않도록 구조를 바꿔) 요청 1건당 커넥션 사용을 2개가 아니라 1개로 줄이는 것이다. 그러면 풀 크기를 이만큼 여유 있게 잡지 않아도 되고, "바깥 트랜잭션이 락 대기 시간 내내 커넥션을 놀리면서 쥐고 있는" 비효율 자체가 없어진다. 이번 세션에서는 풀 크기 조정만으로 대응했고, 이 구조 변경은 범위 밖으로 남긴다.
2. **`point.member_id` 외에 유사한 "최초 생성 레이스" 패턴이 다른 도메인에도 있는지**는 전수 조사하지 않았다. 이번엔 포인트 충전에서 실제로 걸려서 발견했을 뿐이다.
3. **대기열 승격의 멀티 인스턴스 경합**(§5)은 리스트업만 하고 테스트·수정 모두 하지 않았다. 멀티 인스턴스 배포로 전환할 계획이 있다면 우선순위를 올려야 한다.
4. **회원가입 loginId 중복 시 에러 코드(500 → 409)** 수정이 필요하다면, 결제 멱등성과 동일한 패턴(저장소에서 `DataIntegrityViolationException` → `LoginIdDuplicateException` 변환)으로 쉽게 고칠 수 있다 — 이번엔 "중" 위험도라 범위 밖으로 남겼다.
5. **`AdmissionPolicy`/`ThresholdAdmissionPolicy` 삭제**: 대기열 정원 로직을 Lua 원자화로 옮기면서 더 이상 쓰이지 않게 돼 삭제했다. 혹시 다른 용도로 남겨두고 싶다면 복원해야 한다(git 이력에 남아있음).

---

## 11. 한계

- RED 재현은 git stash로 "이번 세션에서 수정한 프로덕션 코드"만 되돌려 측정했다. 그보다 더 이전 시점(이번 세션 시작 전)의 코드 상태 전체를 별도로 체크아웃해 측정하지는 않았다 — 다만 stash로 되돌린 코드가 정확히 세션 시작 시점(`HEAD`)의 코드와 동일함은 `git status`/`git stash`의 표준 동작으로 보장된다.
- 결제 멱등성의 "락 leaseTime 초과로 락이 뚫리는" 시나리오는 타이밍 의존적이라 테스트로 강제 재현하지 않았다(이미 handoff에 구조적 위험으로 기록된 내용을 재확인만 함).
- 대기열 승격의 멀티 인스턴스 경합은 단일 JVM 테스트로는 사실적으로 재현하기 어려워 테스트하지 않았다.
- 동시성 테스트는 타이밍에 의존하는 특성상 환경에 따라 결과가 달라질 수 있다 — 포인트 "다른 예약 2건 동시 결제" 테스트는 이번 환경에서 재현되지 않았지만, 더 느린(또는 더 빠른) 환경에서는 다를 수 있다. 다만 수정(조건부 UPDATE)은 타이밍과 무관하게 항상 안전하다.
- **코드 리뷰 MEDIUM 항목 — `INSERT ... ON DUPLICATE KEY UPDATE` 데드락 재현 시도, 재현 안 됨**(`docs/agent/reviews/2026-10-21-concurrency-review.md`): "포인트 행이 없는 신규 회원에게 여러 트랜잭션이 동시에 최초 충전하면, 아직 존재하지 않는 `member_id` 유니크 키를 두고 InnoDB 갭락(gap lock) 경합으로 데드락(에러 1213)이 날 수 있다"는 지적을 `PointUpsertDeadlockReproductionTest`(`src/test/java/kr/hhplus/be/server/concurrency/`, `@Tag("perf")`로 분리 — 기본 `./gradlew test`에는 포함되지 않고 `./gradlew perfTest`로만 실행됨)로 재현 시도했다.
  - **시도 조건**: 스레드 수 30/40/50 각각에 대해 신규 회원을 새로 만들어 5회씩 반복(총 15회, 회당 해당 스레드 수만큼 동시 `PointFacade.chargePoint` 호출). 결제 분산락 테스트에서 이미 드러난 HikariCP `maximum-pool-size: 3` 병목(§10-1)과 "진짜 DB 레벨 동시성"을 구분하기 위해, 이 테스트에서만 `@TestPropertySource`로 풀 크기를 60으로 올려 격리했다(운영 설정은 건드리지 않음).
  - **결과**: 15회 전부 **재현 안 됨** — 모든 조합(threads=30/40/50 × 5회)에서 100% 성공(0건 실패, 예외 없음)이었다. MySQL 8.0(Testcontainers) + 이 스키마/트랜잭션 패턴에서는 이번 시도 조건으로 데드락이 관찰되지 않았다.
  - **한계**: 재현 안 됨이 "안전하다"를 증명하지는 않는다 — 더 많은 스레드, 다른 MySQL 버전/설정(바이너리 로그 포맷, 격리 수준 등), 또는 실제 운영 트래픽 패턴에서는 다를 수 있다. 재현되지 않아 대응(재시도 로직 등)은 설계·구현하지 않았다 — 필요해지면 `CannotAcquireLockException`/`DeadlockLoserDataAccessException`을 잡아 짧은 backoff 후 재시도하는 정도로 충분할 것으로 보이나, 실제 구현 여부는 재현 증거가 없는 한 보류한다(메인 세션 판단 대기).
- **테스트 설계 중 발견한 사실 — JVM 시계와 DB 시계를 섞어 비교하면 플레이키해진다**: `PointRepositoryIntegrationTest`에서 "usePoint 호출 전/후 `updated_at`이 반드시 더 나중이어야 한다"를 검증하려고 처음엔 `fixture.chargePoint()`(JPA `save()` → `@LastModifiedDate` 감사 리스너, JVM 시계)로 초기 잔액을 만들고 `usePoint`(네이티브 `now(6)`, DB 시계)로 비교했는데 실패했다 — 두 값의 시계 출처가 달라, 호스트/Testcontainers 간의 시계 차이만으로도 순서가 뒤바뀔 수 있었다(실측으로 재현). `pointRepository.chargePoint`(역시 네이티브 `now(6)`)로 초기 잔액을 만들도록 바꿔 같은 DB 시계로 통일하자 안정적으로 통과했다. 프로덕션 코드의 버그는 아니지만(최초 `save()`의 JPA 감사값과 이후 네이티브 쿼리의 DB 시계가 섞여 있다는 사실 자체는 이번 수정 범위 밖), 시계 출처가 다른 타임스탬프를 테스트에서 직접 비교할 때는 주의가 필요하다는 것을 기록해 둔다.

---

## 12. 코드 리뷰 반영 요약

- **대상**: `docs/agent/reviews/2026-10-21-concurrency-review.md` (판정 WARNING, CRITICAL 0 · HIGH 1 · MEDIUM 4 · LOW 2).
- **반영 내역**:
  - HIGH — `point.member_id` UNIQUE의 운영 반영 위험: §4.1에 배포 전 점검 쿼리와 정리 절차(제안)를 추가했다.
  - MEDIUM — 결제 중복 예외 변환 범위: `payment.reservation_id` 제약에 이름(`uk_payment_reservation_id`)을 부여하고, `PaymentJpaRepository.save()`가 그 제약 위반일 때만 `DuplicatePaymentException`으로 변환하도록 좁혔다(§4.2, 실측한 MySQL 예외 메시지·제약 이름 포맷 포함).
  - MEDIUM — UPSERT 데드락 재현 시도: 스레드 30/40/50 × 5회(총 15회) 시도했으나 **재현 안 됨**(§11). 대응은 구현하지 않고 제안만 남겼다.
  - LOW — `usePoint` `updated_at` 미갱신: `chargePoint`와 동일한 네이티브 `now(6)`로 갱신하도록 수정(JPQL `CURRENT_TIMESTAMP`는 정밀도가 달라 1차 시도에서 테스트 실패로 걸러짐).
  - LOW — 포인트 저장소 단위 검증 부재: `PointRepositoryIntegrationTest` 4건 추가(신규 생성/기존 증가/정상 차감/잔액 부족).
  - 커넥션 풀(`maximum-pool-size: 3`, §10-1)은 리뷰 당시엔 "사용자 결정 필요"로 남겨뒀던 항목이다 — 이후 사용자 결정에 따라 **15로 변경**하고 근거·실측 결과를 §10-1에 갱신했다(별도 세션, 2026-10-21).
- **오판 1건과 근거**: 리뷰는 "대기열 재발급 시 TTL 연장 동작이 사라졌다"(MEDIUM)고 지적했지만, 메인 세션이 수정 전(HEAD) `IssueQueueTokenService`를 직접 확인한 결과 수정 전 코드도 활성 토큰이 있으면 기존 토큰을 그대로 반환하고 TTL을 연장하지 않는 동작이었다 — 즉 이번 수정이 "없앤" 동작이 아니라 원래부터 없던 동작이었다. 리뷰어가 코드 읽기·추론만으로(테스트 미실행) 수정 전/후를 비교하다 생긴 오판으로 판단해 별도 조치는 하지 않았다.
- **변경하지 않은 것**: 좌석 조건부 UPDATE·Redis Lua(기존 리뷰 통과 영역), 대기열 Lua(`tryIssueActive`), 포인트 UPSERT/조건부 UPDATE의 의미 — 모두 그대로 유지했고, 이번엔 예외 변환 범위(§MEDIUM 2)와 `updated_at` 갱신(§LOW 1)만 좁혀서 고쳤다.
