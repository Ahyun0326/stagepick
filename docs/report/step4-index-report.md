# STEP 4 선택 심화 ① — 느린 조회 리스트업 · 인덱스 분석 보고서

> 항해플러스 STEP 4 "DB 구조로 데이터 정합성 확보" 선택 심화 ①.
> 모든 수치는 `./gradlew perfTest`로 실측한 값이며, 측정하지 않은 수치는 적지 않았다.
> 측정 하네스: `src/test/java/kr/hhplus/be/server/perf/` · 원본 출력: `docs/report/data/`

---

## 1. 요약 (결론)

1. **콘서트 예약 서비스의 조회 경로는 이미 거의 모든 곳에 인덱스가 있었다.** 선택 심화의 본질은 "인덱스 추가"가 아니라 **"있는 인덱스가 실제로 쓰이는지 실측으로 검증"** 하는 것이었다.
2. **FK가 걸린 컬럼의 인덱스(`schedules.idx_concert_id_concerted_at`, `payment.idx_reservation_id`)는 InnoDB가 DROP 자체를 거부**했다(`Cannot drop index ...: needed in a foreign key constraint`, 실측 확인). "없을 때"를 측정하려면 FK를 먼저 떼어내야 했다 — 이 자체가 그 인덱스들이 구조적으로 필수라는 증거다.
3. **인덱스가 있고 없고의 차이가 가장 크게 나는 지점은 좌석/결제의 역방향 조회였다**: `seat.reservation_id`, `payment.reservation_id` 로 역조회하는 쿼리는 인덱스 없이는 각각 **101~103ms, 37.1~42.5ms**(500,000행/100,000행 풀스캔)였던 것이 인덱스가 있으면 **0.004~0.008ms**로, **약 5,000~25,000배** 빨라졌다(측정 2회차 범위). 이 두 경로는 결제 대기 조회(`GET /api/v1/payments/{reservationId}`)와 결제 처리(`POST /api/v1/payments`)에서 **한 요청에 두 번씩** 실행돼 위험도가 가장 높다.
4. **중복 인덱스 2건(`payment.idx_number`, `member.idx_uuid`)을 제거**했다. 둘 다 이미 `UNIQUE` 제약이 같은 컬럼에 자동으로 인덱스를 만들어 주고 있어 옵티마이저가 항상 그 `UNIQUE` 키를 썼고(EXPLAIN의 `key` 컬럼이 제거 전후 완전히 동일), 읽기 시간도 비교할 의미가 없었다 — UNIQUE 키 상수 조회(`type=const`)는 실행 전 계획 단계에서 행을 가져오므로 EXPLAIN ANALYZE 값이 수십 나노초(0.00004~0.00008ms)로 찍힌다. 쓰기 비용(2만 건 배치 INSERT)도 노이즈 범위 안이라 유의미한 차이는 확인하지 못했지만, **읽기에 아무 도움이 안 되는 인덱스를 유지할 이유가 없어** 제거했다.
5. **제안했던 후보 인덱스 `seat(schedule_id, id)`는 채택하지 않았다.** 회차별 좌석 목록 조회(`ORDER BY id`)가 filesort를 쓰는 건 사실이지만 실측 비용이 0.03~0.06ms로 무시할 수준이었고, 인덱스를 추가해도 **옵티마이저가 기존 `uk_schedule_id_number`를 그대로 선택**해 후보 인덱스는 전혀 쓰이지 않았다(EXPLAIN의 `key`가 제거 전후 동일). 쓰기 비용만 더하는 인덱스라 추가하지 않았다.

---

## 2. 측정 환경

| 항목 | 값 |
|---|---|
| DB | MySQL **8.0.46** (Testcontainers `mysql:8.0`, `./gradlew test`/`perfTest`가 쓰는 것과 동일 이미지) |
| Redis | `redis:7-alpine` (이 보고서의 측정 대상은 아니지만 같은 컨테이너 세트로 기동) |
| 격리 | `IntegrationTestBase`/`TestcontainersConfiguration`과 동일한 싱글톤 컨테이너 재사용. 단, perf 테스트는 매 테스트마다 TRUNCATE 하지 않고 **클래스당 1회만 시딩** (`PerfTestBase`, `IndexPerfTest.@BeforeAll`) |
| 스키마 생성 | `spring.jpa.hibernate.ddl-auto=create-drop` (integration 프로파일 기본값), 현재 엔티티 매핑 그대로 |
| 시딩 방식 | Hibernate를 거치지 않고 **순수 JDBC batch INSERT** (`rewriteBatchedStatements=true`로 다중 VALUES INSERT 재작성), PK는 직접 지정. 전체 시딩 소요 **약 29초** |
| 반복 횟수 | SELECT: `EXPLAIN ANALYZE` 5회 반복 → 중앙값 / UPDATE·INSERT: `System.nanoTime()` 5회(UPDATE) 또는 3회(2만 건 INSERT) 반복 → 중앙값 |
| 실행 방법 | `./gradlew perfTest` (기본 `./gradlew test`에는 `@Tag("perf")`로 제외됨 — `build.gradle.kts`) |
| 전체 perfTest 소요 | 약 1분 10초~1분 17초 (시딩 약 29초 포함, 테스트 메서드 8개). 메인 세션 재실행(2회차)으로 수치 재현 확인 |
| 원본 출력 | `docs/report/data/*.txt` (EXPLAIN/EXPLAIN ANALYZE 전체 원문), `docs/report/data/summary.csv` (표로 옮긴 요약) |

### 데이터 규모 (실제 적재된 건수, `perfTest` 실행 시 자동 확인)

| 테이블 | 건수 | 분포 비고 |
|---|---:|---|
| concert | 1,000 | - |
| schedules | 10,000 | 상위 20% 콘서트(200개)에 30회차씩(6,000건), 나머지 800개 콘서트에 5회차씩(4,000건) — 인기 콘서트 쏠림 재현 |
| seat | 500,000 | 회차당 50석(A1~E10) |
| member | 100,000 | - |
| reservation | 200,000 | 회원당 평균 2건 |
| payment | 100,000 | 예약 중 하위 ID 100,000건만 결제 완료(홀드→결제 전환율 가정) |
| point | 100,000 | 회원당 1건 |
| point_history | 300,000 | 회원당 3건(CHARGE/USE 교대) |

과제 예시(좌석 50만 등)를 그대로 따랐고, 디스크 여유(측정 시작 시 host 기준 약 11GB)를 고려해 `point_history`만 "수십만" 하한선인 30만으로 설정했다. 실제 적재 후 디스크 사용량 증가는 host 기준 약 2GB 이내였다(추가 이미지 레이어 포함).

---

## 3. 쿼리·조건부 UPDATE 리스트업

| # | 쿼리/조작 | 호출 경로 | 빈도 추정 | 데이터 증가 축 | 위험도 | 이유 |
|---|---|---|---|---|---:|---|
| 1 | `SpringSeatJpa.findAllByScheduleId` (회차별 좌석 목록, `ORDER BY id`) | `GET /api/v1/reservation/seats/{scheduleId}` ← `SeatFacade.getSeatsInfo` ← `GetSeatsInfoService` | **상** — 대기열 통과 직후 좌석 선택 화면, 트래픽 집중 | `seat` 행 수(스케줄×좌석 수) | 중 | 회차당 행 수는 적어 풀스캔이어도 느리지 않지만, **호출 빈도가 가장 높은 화면**이라 회귀에 취약 |
| 2 | `SpringSeatJpa.findByScheduleIdAndSeatIds` | `POST /api/v1/reservation/seats`(예약 생성) ← `ReserveSeatService`, 분산락(`schedule:{id}:seat-reservation`) 내부 | 상 — 예약 시도마다 | `seat` 전체 행 수 | 중 | PK `IN` 조건이 매우 선택적이라 옵티마이저가 PRIMARY를 선택(실측, §9) — 락 보유 중 실행되므로 느려지면 락 대기 시간에 직결 |
| 3 | `SpringSeatJpa.findAllByReservationId` | `GET /api/v1/payments/{reservationId}`(결제 대기 조회) ← `FindPendingPaymentInfoService` **그리고** `POST /api/v1/payments`(결제 처리) ← `ProcessPaymentService.getSeats` | **상** — 결제 대기 화면 + 결제 확정, **한 예약당 최소 2회** | `seat` 전체 행 수(500,000) | **상** | 인덱스 없이는 전체 테이블 스캔(§6-2 실측 101ms) — 두 API 모두에서 쓰이는 공용 경로 |
| 4 | `SpringSeatJpa.holdAvailableSeats` (조건부 UPDATE, `id IN ... AND status='AVAILABLE'`) | `POST /api/v1/reservation/seats` ← `ReserveSeatService`, 분산락 내부 | 상 — 예약 시도마다 | `seat` 전체 행 수 | 중 | PK 범위 스캔(실측 §6-5) — 동시성 방어선이라 **의미를 바꾸면 정합성이 깨짐**, 이번 보고서에서는 쿼리 형태를 바꾸지 않음 |
| 5 | `SpringSeatJpa.releaseHeldSeats` (조건부 UPDATE, `id IN ... AND status='HOLD'`) | `SeatHoldScheduler.expireSeatHold` (`@Scheduled(fixedRate = 1000)`, **1초마다**) ← `ExpireSeatHoldService` | **상** — 1초 주기 스케줄러, 만료 대상이 있을 때마다 | `seat` 전체 행 수 | 중 | PK 범위 스캔(실측) — **주기적 배치라 느려지면 다음 틱까지 누적**될 수 있음 |
| 6 | `SpringSeatJpa.confirmHeldSeats` (조건부 UPDATE, `id IN ... AND status='HOLD' AND reservation_id=...`) | `POST /api/v1/payments` ← `ProcessPaymentService.confirmReservation`, 분산락(`reservation:{id}:payment`) 내부 | 상 — 결제 시도마다 | `seat` 전체 행 수 | 중 | PK 범위 스캔(실측) — 정합성 방어선, 쿼리 의미 변경 금지 |
| 7 | `ConcertRepositoryImpl.findConcerts` (콘서트 목록 MIN/MAX 집계) | `GET /api/v1/concerts` | **상** — 서비스 진입점(첫 화면) | `concert`×`schedules` 모두 | 중 | 집계라 WHERE 선택성이 없어 인덱스 유무와 무관하게 `concert`/`schedules` 모두 거의 전수 스캔(실측 §6-1 — 인덱스가 있어도 6.2ms vs 없으면 5.75ms, 유의미한 차이 없음) |
| 8 | `ConcertRepositoryImpl.findConcertDetailById` | `GET /api/v1/concerts/{concertId}` | 중 | `schedules` | 하 | `concert.id` PK(const) + `schedules.idx_concert_id_concerted_at`(ref, rows=5) — 이미 최적(§9) |
| 9 | `ScheduleRepositoryImpl.findAvailableConcerts` | `GET /api/v1/concerts/{concertId}/schedules` | 중 | `schedules` | 하 | 같은 인덱스로 선택적 조회 — 인덱스 있으면 0.0045ms, 없으면 1.46ms(실측, §6-1b) |
| 10 | `ReservationQueryRepositoryCustomImpl.getWithDetailsById` (seat⋈schedules⋈concert) | `GET /api/v1/payments/{reservationId}` + `POST /api/v1/payments`(결제 스냅샷 생성) | **상** — #3과 동일하게 한 예약당 2회 이상 | `seat` 전체 행 수 | **상** | #3과 같은 이유로 `seat.reservation_id` 인덱스에 의존 — 조인까지 포함해 인덱스 없이는 86.8ms(실측) |
| 11 | `SpringPaymentJpa.findByReservationId` (중복 결제 검증) | `POST /api/v1/payments` ← `ProcessPaymentService.validatePaymentRequest` | 상 — 결제 시도마다 | `payment` 전체 행 수(100,000) | **상** | 인덱스 없이는 37.9ms 풀스캔(실측) — 결제 경로 첫 단계라 느려지면 분산락 보유 시간이 늘어남 |
| 12 | `SpringPointJpa.findByMemberId` | `POST /api/v1/points`(충전), `GET /api/v1/points`(조회), `POST /api/v1/payments`(포인트 차감) | 상 | `point` | 하 | `idx_member_id` ref 단건 — 이미 최적(§9) |
| 13 | `SpringPointHistoryJpa` 커스텀 조회 | 없음 — `save()`만 호출됨(`PointHistoryJpaRepository`) | - | - | - | **현재 이 인덱스(`idx_member_id_created_at`)를 쓰는 조회가 없다.** ERD 설계상의 "포인트 내역 조회" API가 아직 없어 미사용 상태. 이번 보고서 범위(기존 쿼리 실측)에서는 제거/유지를 판단할 "쓰이는 쿼리"가 없어 현상 유지(사실만 기록) |
| 14 | `MemberRepository.findByLoginId` | `POST /api/v1/auth/signin` | 중 | `member` | 하 | `UNIQUE` 인덱스 const 조회 — 이미 최적(§9) |
| 15 | `MemberRepository.findByUuid` | **인증된 모든 요청** ← `MemberAuthenticatedMemberReader.resolveMemberId` (JWT uuid → memberId 변환, Reservation/Payment/Point Facade 공통 포트) | **최상** — 보호된 API 전체에서 요청마다 1회 | `member` | 하 | `UNIQUE` 인덱스 const 조회(계획 단계에서 해결, EXPLAIN ANALYZE 0.00004~0.00008ms) — 빈도는 가장 높지만 이미 최적이라 위험도는 낮음 |

**위험도 "상" 요약**: #3(`findAllByReservationId`), #10(`getWithDetailsById`), #11(`findByReservationId`) — 모두 "결제 대기 조회 + 결제 처리"라는 같은 좁은 경로에 몰려 있고, 인덱스가 없었다면 각각 수십~백 ms대의 풀스캔이 됐을 쿼리다. 다행히 셋 다 이미 전용 인덱스가 있었고, 이번 실측으로 "왜 필요한지"를 수치로 확인했다.

---

## 4. 측정 결과 — Before / After

> `rows`는 MySQL 통계 기반 **추정치**이며 실행마다 조금씩 흔들린다(옵티마이저가 실제로 읽은 행 수가 아니다 — §6 참고). `시간`은 EXPLAIN ANALYZE 또는 wall-clock 5회(또는 3회) 반복의 **중앙값**이다.

### 4-1. `schedules(concert_id, concerted_at)` — 콘서트 목록 집계 vs 회차 단건 조회

인덱스가 FK(`concert_id`)에 걸려 있어 DROP INDEX 자체가 거부됐다(`Cannot drop index idx_concert_id_concerted_at: needed in a foreign key constraint`, 실측). FK를 먼저 떼고 측정했다.

| 쿼리 | 상태 | table | type | key | rows | Extra | 시간(ms) |
|---|---|---|---|---|---:|---|---:|
| 콘서트 목록 집계(`findConcerts`) | before(인덱스 없음) | c | ALL | – | 1000 | Using temporary | 5.75 |
| 〃 | 〃 | s | ALL | – | 10234 | Using where; Using join buffer (hash join) | (join, 전체시간은 c행 참고) |
| 〃 | after(현재 코드) | c | ALL | – | 1000 | Using temporary | 6.2 |
| 〃 | 〃 | s | ref | idx_concert_id_concerted_at | 9 | Using index | 〃 |
| 회차 단건 조회(`findAvailableConcerts`, `WHERE concert_id=500`) | before(인덱스 없음) | schedules | ALL | – | 9896 | Using where | **1.46** |
| 〃 | after(현재 코드) | schedules | ref | idx_concert_id_concerted_at | 5 | – | **0.0045** |

- 집계 쿼리는 `GROUP BY` + `LEFT JOIN`에 선택적 조건이 없어서 옵티마이저가 (인덱스가 있어도) `Using temporary`로 전체를 모아야 한다. 인덱스가 있으면 네스티드 루프+커버링 인덱스 탐색(1,000번), 없으면 해시 조인(1회 풀스캔)으로 전략만 바뀔 뿐 — 실측상 **6.2ms vs 5.75ms로 거의 차이 없음(오히려 인덱스 있는 쪽이 근소하게 느림, 노이즈 수준)**.
- 반면 `concert_id` 단건 조회는 **1.46ms → 0.0045ms, 약 324배** 차이가 났다. **같은 인덱스가 어떤 쿼리 모양에는 거의 무용하고 어떤 쿼리 모양에는 결정적**이라는 걸 같은 인덱스로 보여준 사례.

### 4-2. `seat.idx_reservation_id` / `payment.idx_reservation_id` — 역방향 조회 3종

`seat.idx_reservation_id`는 FK가 아니라 DROP이 바로 됐다. `payment.idx_reservation_id`는 FK라 schedules와 동일하게 FK를 먼저 떼고 측정했다.

| 쿼리 | 상태 | table | type | key | rows | Extra | 시간(ms) |
|---|---|---|---|---|---:|---|---:|
| `findAllByReservationId`(`seat WHERE reservation_id=777`) | before | seat | ALL | – | ~497,000~499,000 | Using where | **101** |
| 〃 | after | seat | ref | idx_reservation_id | 2 | – | **0.004~0.005** |
| `getWithDetailsById`(seat⋈schedules⋈concert) | before | seat | ALL | – | ~497,000~499,000 | Using where | **81.8~86.8** |
| 〃 | 〃 | schedules | eq_ref | PRIMARY | 1 | Using where | (join) |
| 〃 | 〃 | concert | eq_ref | PRIMARY | 1 | – | (join) |
| 〃 | after | seat | ref | idx_reservation_id | 2 | Using where | **0.0115~0.0128** |
| 〃 | 〃 | schedules | eq_ref | PRIMARY | 1 | Using where | (join) |
| 〃 | 〃 | concert | eq_ref | PRIMARY | 1 | – | (join) |
| `findByReservationId`(`payment WHERE reservation_id=777`) | before | payment | ALL | – | 98325 | Using where | **37.1~37.9** |
| 〃 | after | payment | ref | idx_reservation_id | 1 | – | **0.0044~0.0081** |

**약 2만~2.5만 배(`seat` 단독) / 약 6,400~8,400배(3-way join) / 약 4,600~8,600배(`payment`)** 차이(측정 2회차 범위). 이 세 쿼리가 "위험도 상"인 이유를 가장 분명하게 보여주는 수치다.

### 4-3. 중복 인덱스 제거 — `payment.idx_number`, `member.idx_uuid`

엔티티에서 이미 제거했으므로 "제거 후(현재 코드)"가 기본 상태이고, "제거 전"은 거꾸로 임시로 인덱스를 추가해 재현했다.

| 쿼리 | 상태 | key (EXPLAIN) | 시간(ms) |
|---|---|---|---:|
| `payment WHERE number='...'` | 제거 후(현재 코드, UNIQUE만) | UK60kxdvnm4uoig1o1wwnd6j6f3 | 0.000041 |
| 〃 | 제거 전(UNIQUE+idx_number 둘 다, 가정) | UK60kxdvnm4uoig1o1wwnd6j6f3 | 0.000041~0.000042 |
| `member WHERE uuid='...'` | 제거 후(현재 코드, UNIQUE만) | UKerrlnismyy5xmphdt6e4l0oi1 | 0.000042~0.000083 |
| 〃 | 제거 전(UNIQUE+idx_uuid 둘 다, 가정) | UKerrlnismyy5xmphdt6e4l0oi1 | 0.000042~0.000083 |

**EXPLAIN의 `key`가 제거 전/후 완전히 동일**(항상 `UNIQUE` 제약 인덱스) — 옵티마이저가 중복 인덱스를 아예 고려 대상에 넣지 않았다는 뜻. 시간 값도 동일하지만 const 조회라 계획 단계 값(수십 나노초)이어서 비교 근거로 쓰지 않았다.

쓰기 비용(`payment`와 동일 구조의 clone 테이블에 2만 건 batch INSERT, 3회 반복 중앙값):

| 상태 | 시간(ms) |
|---|---:|
| 제거 후(현재 코드, idx_number 없음) | 579.7 |
| 제거 전(idx_number 있음, 가정) | 589.8 |

방향(제거 쪽이 더 빠름)은 가설과 일치하지만 **3회 반복의 개별 샘플이 614~812ms로 넓게 흩어져 있어, 이 차이가 노이즈보다 크다고 결론 내릴 수 없다.** (§7 한계 참고) 그래서 "쓰기가 빠져졌다"는 주장은 하지 않고, "읽기에는 전혀 도움이 안 되는 게 확인됐다"만을 제거 근거로 삼았다.

### 4-4. 회차별 좌석 조회 `ORDER BY id` — filesort + 후보 인덱스

| 쿼리 | 상태 | key | Extra | 시간(ms) |
|---|---|---|---|---:|
| `seat WHERE schedule_id=5000 ORDER BY id` | before(현재 코드) | uk_schedule_id_number | Using filesort | 0.052~0.056 |
| 〃 | after(`(schedule_id, id)` 후보 인덱스 추가) | uk_schedule_id_number | Using filesort | 0.031~0.034 |

**`key`와 `Extra`가 후보 인덱스 추가 전/후 동일** — 옵티마이저가 새 인덱스를 아예 선택하지 않았다(회차당 50행뿐이라 filesort 비용이 이미 무시할 수준이라고 판단한 것으로 보인다). 시간차(0.05ms → 0.03ms)는 반복 간 노이즈로 보는 게 합리적이다. **후보 인덱스는 도입하지 않았다.**

### 4-5. 조건부 UPDATE 3종 — PK 사용 확인

MySQL 8.0.46에서 `EXPLAIN ANALYZE UPDATE ...`는 `<not executable by iterator executor>`로 거부됨(실측 확인) → `EXPLAIN`(계획)만 수집하고, 시간은 매 반복 전 상태를 복원하며 `System.nanoTime()`으로 직접 측정했다.

| UPDATE | table | type | key | rows | Extra | 시간(ms, wall-clock 5회 중앙값) |
|---|---|---|---|---:|---|---:|
| `holdAvailableSeats`(`id IN (...) AND status='AVAILABLE'`) | seat | range | **PRIMARY** | 3 | Using where | 1.16~1.89 |
| `releaseHeldSeats`(`id IN (...) AND status='HOLD'`) | seat | range | **PRIMARY** | 3 | Using where | 1.02~1.15 |
| `confirmHeldSeats`(`id IN (...) AND status='HOLD' AND reservation_id=...`) | seat | range | **PRIMARY** | 3 | Using where | 0.97~1.09 |

셋 다 `id IN (...)`이 PK 범위 스캔으로 처리되고, `status`/`reservation_id` 조건은 "Using where"로 가져온 행(3건)에 대해서만 추가로 걸러진다. **이미 최적이라 인덱스를 추가할 필요가 없다** — 정합성 방어선의 쿼리 형태(의미)도 바꾸지 않았다.

### 4-6. 기타 — 이미 최적으로 확인된 조회 (before/after 비교 없이 현재 상태만)

| 쿼리 | table | type | key | rows | filtered | Extra |
|---|---|---|---|---:|---:|---|
| `findByScheduleIdAndSeatIds`(`schedule_id=5000 AND id IN(...)`) | seat | range | PRIMARY | 3 | 1.67 | Using where |
| `findConcertDetailById`(`concert_id=500`) | c | const | PRIMARY | 1 | 100.0 | – |
| 〃 | s | ref | idx_concert_id_concerted_at | 5 | 100.0 | – |
| `findByLoginId` | member | const | UKenfm5patwjqulw8k4wwuo6f60 | 1 | 100.0 | – |
| `point.findByMemberId` | point | ref | idx_member_id | 1 | 100.0 | – |

`findByScheduleIdAndSeatIds`가 `schedule_id` 인덱스가 아니라 **PK(`id IN`)를 선택**한 게 눈에 띈다 — `id IN` 리스트가 매우 선택적(3건)이라 옵티마이저가 더 싼 경로로 PK를 골랐다. 인덱스 추가 없이 이미 최적.

---

## 5. 결정과 근거

| 대상 | 결정 | 근거 |
|---|---|---|
| `schedules.idx_concert_id_concerted_at` | **유지** (변경 없음) | FK 지원에 구조적으로 필수(DROP 자체가 거부됨, 실측). 집계 쿼리엔 거의 무용하지만 회차 단건 조회(`findAvailableConcerts`)엔 324배 효과(§4-1) |
| `seat.idx_reservation_id` | **유지** (변경 없음) | §4-2 실측상 "상" 위험도 쿼리 2개(#3, #10)를 약 6,400~25,000배 단축. FK는 아니지만 효과가 가장 크다 |
| `payment.idx_reservation_id` | **유지** (변경 없음) | FK 지원에 구조적으로 필수 + §4-2 실측상 5,000~8,000배 단축 |
| `payment.idx_number` | **제거** — `PaymentEntity.kt` | `number` 컬럼의 `@Column(unique = true)`가 이미 같은 컬럼에 UNIQUE 인덱스를 만든다. EXPLAIN의 `key`가 제거 전/후 완전히 동일(§4-3) — 옵티마이저가 쓴 적이 없는 중복 인덱스. 읽기 이득이 전혀 없으므로 유지 비용(쓰기 시 B-Tree 하나 더 관리)만 있는 인덱스로 판단해 제거 |
| `member.idx_uuid` | **제거** — `Member.kt` | `uuid` 컬럼의 `@Column(unique = true)`와 동일한 중복 패턴(§4-3). 이 쿼리는 **인증된 모든 요청**(#15)에서 실행되는, 이 보고서에서 빈도가 가장 높은 조회라 "중복 인덱스가 전혀 득이 안 된다"는 확인의 체감 가치가 크다 |
| `seat(schedule_id, id)` 후보(신규) | **도입하지 않음** | §4-4 실측상 옵티마이저가 선택하지 않았고(기존 `uk_schedule_id_number`를 그대로 씀), 시간 차이도 노이즈 수준. 쓰기 비용만 추가되는 인덱스 |
| 조건부 UPDATE 3종(`holdAvailableSeats`/`releaseHeldSeats`/`confirmHeldSeats`) | **변경 없음** | §4-5 실측상 이미 PK 범위 스캔 — 추가할 인덱스가 없고, 정합성 방어선의 쿼리 의미도 바꾸지 않음(요구사항 제약) |
| 회차별 좌석 목록 `ORDER BY id`의 filesort | **변경 없음** | §4-4 실측상 50행 규모에서 filesort 비용이 무시할 수준(0.03~0.06ms) |

### 운영 반영 경로에 대한 확인 (미검증 → 실측으로 전환)

`docs/agent/handoff.md`에 "prod `ddl-auto: update`가 인덱스 추가/삭제를 반영하는지는 **미검증**"이라고 적혀 있었는데, 이번에 직접 실측했다:

1. 현재 엔티티(= `idx_number`/`idx_uuid` 없음)로 `create-drop` 스키마를 만든 뒤, raw JDBC로 `idx_number`/`idx_uuid`를 수동으로 다시 추가해 "마이그레이션 전 운영 DB" 상태를 흉내냈다.
2. 같은 DB에 대해 `ddl-auto=update` + 현재 엔티티 매핑으로 **새 Spring 컨텍스트**를 띄워 `SchemaUpdate`를 유도했다.
3. `SHOW CREATE TABLE`로 확인한 결과, **`idx_number`/`idx_uuid` 둘 다 그대로 남아 있었다** — Hibernate의 `update` 모드는 "추가만 하고 제거는 하지 않는다"는 문서상의 동작을 실측으로 확인했다.

**결론: 이 보고서에서 제거한 두 인덱스는 운영 DB에 배포된 뒤 `ddl-auto=update`만으로는 지워지지 않는다.** 실제 운영에 반영하려면 수동 마이그레이션(`ALTER TABLE ... DROP INDEX ...`)이 필요하다. (이 조사는 1회성이라 재현 테스트 코드는 남기지 않았고, 본 보고서에만 기록한다.)

---

## 6. 채택하지 않은 대안

- **`seat(schedule_id, id)` 커버링/정렬용 신규 인덱스**: §4-4, §5 참고. 실측상 옵티마이저가 안 쓰고 효과도 없어 폐기.
- **`payment.idx_number`/`member.idx_uuid`를 유지하되 쓰기 비용 우려만 기록**: 쓰기 비용 차이가 노이즈 범위라 "쓰기가 빨라진다"는 확실한 근거로는 못 쓰지만, 읽기에 전혀 도움이 안 된다는 확실한 근거(§4-3)가 있어 제거 쪽을 택했다. 애매한 쓰기 비용 수치를 근거로 올리지 않았다.
- **조건부 UPDATE 3종의 쿼리 형태 변경**: 과제 제약("정합성 관련 조건부 UPDATE의 의미는 바꾸지 말 것")에 따라 처음부터 변경 후보에서 제외했고, 실측으로도 변경할 이유가 없음을 확인했다(§4-5).
- **`point`/`point_history`의 인덱스 재설계**: `point_history.idx_member_id_created_at`을 쓰는 조회 쿼리가 현재 코드에 없다는 사실은 확인했지만(§3, #13), 이건 "미사용 인덱스를 지울까"의 문제가 아니라 "포인트 내역 조회 API 자체가 아직 없다"는 기능 공백이라 이번 인덱스 보고서 범위를 벗어난다고 판단해 손대지 않았다. `point.member_id`의 UNIQUE 누락 등 제약 갭은 `docs/agent/handoff.md`에 이미 별도로 기록돼 있어 중복 기록하지 않았다.
- **FK 자체(제약)를 떼거나 바꾸는 것**: `schedules`/`payment`의 FK는 이번 측정에서 "DROP INDEX 거부"를 재현하기 위해 **일시적으로만** 제거했다가 테스트 안에서 바로 복원했다. 운영 스키마의 FK 정책 자체는 이번 보고서의 결정 대상이 아니다.

---

## 7. 한계

- **단일 Testcontainers 인스턴스, 콜드 캐시가 아닌 실행 중 캐시 상태**: InnoDB 버퍼 풀에 이미 올라간 데이터로 반복 측정했다. 실제 운영 DB는 메모리 압박·다른 쿼리와의 경합·디스크 I/O 지연이 섞여 있어 이 보고서의 절대 시간(특히 μs~0.0x ms 구간)은 운영에 그대로 적용되지 않는다. **상대적인 before/after 배수**(수천~수만 배)는 구조적 차이(풀스캔 vs 인덱스 탐색)라 환경이 달라도 방향성은 유지될 것으로 본다.
- **`rows`는 추정치**: 같은 쿼리를 다른 시점에 재측정하면 `seat` 풀스캔의 `rows`가 496,593~498,992로 조금씩 바뀌었다(실제 행 수는 항상 정확히 500,000). MySQL 옵티마이저의 통계 기반 추정이라는 걸 실측으로도 확인했다(스터디 가이드의 "rows 값은 실측인가 추정인가?" 질문과 직결).
- **쓰기 비용(2만 건 INSERT) 측정은 샘플 수가 적고(3회) 노이즈가 커서 결정적이지 않다** (§4-3). 더 많은 반복과 더 큰 배치로 재측정하면 방향이 더 분명해질 수 있으나, 이번 보고서에서는 "노이즈 범위"라고만 명시하고 과장된 결론을 내지 않았다.
- **`EXPLAIN ANALYZE`는 UPDATE/DELETE를 지원하지 않는다**(MySQL 8.0.46, 실측 확인 — `<not executable by iterator executor>`). 조건부 UPDATE 3종은 계획(EXPLAIN)과 실행 시간(wall-clock)을 따로 측정했고, 두 측정 사이에 미세한 타이밍 차이가 있을 수 있다.
- **멤버 uuid/결제번호 조회의 시간 값은 비교 대상이 아니다**: `type=const` 조회는 옵티마이저가 실행 전에 행을 가져오므로 EXPLAIN ANALYZE에 수십 나노초(0.00004~0.00008ms)로 찍힌다. 실제 요청 비용(네트워크·커넥션)과 무관한 값이라, "EXPLAIN의 key가 제거 전후 동일하다"는 **구조적** 증거만 근거로 삼았다.
- **FK 컬럼의 before 상태를 만들기 위해 운영에서는 하지 않을 "FK 임시 제거"를 수행**했다. 실제 운영 DB에서 이 작업을 재현하려면 더 신중한 절차(복제본에서 측정 등)가 필요하다 — 이번엔 디스포저블한 Testcontainers 환경이라 가능했다.
- **`ddl-auto=update`가 인덱스를 지우지 않는다는 조사는 1회성**으로, 재현 가능한 테스트 코드로 남기지 않았다(perf 산출물이 아니라 구조 조사였기 때문). 필요하면 같은 절차(§5)로 재현할 수 있다.

---

## 8. 측정 하네스 구성 (재현 방법)

```bash
./gradlew perfTest
```

- `src/test/java/kr/hhplus/be/server/perf/PerfTestBase.kt`: `@Tag("perf")` + 기존 `TestcontainersConfiguration`/`IntegrationTestConfiguration` 재사용, 단 매 테스트 TRUNCATE 없음
- `PerfJdbc.kt`: 시딩 전용 raw JDBC 커넥션(`rewriteBatchedStatements=true`)
- `PerfDataSeeder.kt`: JDBC batch INSERT 시더, 규모는 `Scale` object에 상수로 고정
- `ExplainSupport.kt`: `EXPLAIN`/`EXPLAIN ANALYZE` 실행·반복·중앙값·FK 임시 DROP/복원 유틸리티
- `ReportWriter.kt`: `docs/report/data/`에 원본 텍스트 + `summary.csv` 기록
- `IndexPerfTest.kt`: 측정 시나리오(§4 전체, 테스트 메서드 8개)

`build.gradle.kts`에서 기본 `test` 태스크는 `excludeTags("perf")`로 이 테스트들을 제외하고, `perfTest` 태스크만 `includeTags("perf")`로 실행한다.
