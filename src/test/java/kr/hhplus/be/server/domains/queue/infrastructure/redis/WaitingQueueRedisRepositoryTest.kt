package kr.hhplus.be.server.domains.queue.infrastructure.redis

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import java.util.concurrent.TimeUnit

class WaitingQueueRedisRepositoryTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val redisTemplate: RedisTemplate<String, Any> = mockk()
    val zSetOperations: ZSetOperations<String, Any> = mockk()
    val setOperations: SetOperations<String, Any> = mockk()
    val valueOperations: ValueOperations<String, Any> = mockk()
    val waitingQueueRedisRepository = WaitingQueueRedisRepository(redisTemplate, QueueProperties())

    given("신규 대기자를 등록할 때") {
        val queueProperties = QueueProperties().apply {
            waitingHeartbeatTtlMs = 60_000
        }
        val repository = WaitingQueueRedisRepository(redisTemplate, queueProperties)

        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { redisTemplate.opsForValue() } returns valueOperations
        every { setOperations.add("queue:schedule:waiting", "1") } returns 1
        every { zSetOperations.add("queue:schedule:1:waiting", "member-uuid", any()) } returns true
        every {
            valueOperations.set(
                "queue:schedule:1:waiting:heartbeat:member-uuid",
                "ALIVE",
                60_000,
                TimeUnit.MILLISECONDS
            )
        } returns Unit

        `when`("대기열에 추가하면") {
            repository.add(1L, "member-uuid")

            then("대기 순서 score를 저장하고 heartbeat를 초기화한다") {
                verify(exactly = 1) { zSetOperations.add("queue:schedule:1:waiting", "member-uuid", any()) }
                verify(exactly = 1) {
                    valueOperations.set(
                        "queue:schedule:1:waiting:heartbeat:member-uuid",
                        "ALIVE",
                        60_000,
                        TimeUnit.MILLISECONDS
                    )
                }
            }
        }
    }

    given("대기자가 폴링 중일 때") {
        every { redisTemplate.opsForValue() } returns valueOperations
        every {
            valueOperations.set(
                "queue:schedule:1:waiting:heartbeat:member-uuid",
                "ALIVE",
                60_000,
                TimeUnit.MILLISECONDS
            )
        } returns Unit

        `when`("heartbeat를 갱신하면") {
            waitingQueueRedisRepository.touchHeartbeat(1L, "member-uuid")

            then("heartbeat key TTL을 연장한다") {
                verify(exactly = 1) {
                    valueOperations.set(
                        "queue:schedule:1:waiting:heartbeat:member-uuid",
                        "ALIVE",
                        60_000,
                        TimeUnit.MILLISECONDS
                    )
                }
            }
        }
    }

    given("heartbeat가 끊긴 대기자가 있을 때") {
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { zSetOperations.range("queue:schedule:1:waiting", 0, -1) } returns setOf("uuid-1", "uuid-2")
        every { redisTemplate.hasKey("queue:schedule:1:waiting:heartbeat:uuid-1") } returns true
        every { redisTemplate.hasKey("queue:schedule:1:waiting:heartbeat:uuid-2") } returns false
        every { zSetOperations.remove("queue:schedule:1:waiting", "uuid-2") } returns 1
        every { zSetOperations.size("queue:schedule:1:waiting") } returns 1

        `when`("비활성 대기자를 정리하면") {
            waitingQueueRedisRepository.removeInactiveWaiting(1L)

            then("heartbeat가 없는 사용자만 대기열에서 제거한다") {
                verify(exactly = 1) { zSetOperations.remove("queue:schedule:1:waiting", "uuid-2") }
                verify(exactly = 0) { zSetOperations.remove("queue:schedule:1:waiting", "uuid-1") }
            }
        }
    }

    given("비활성 대기자 정리 후 스케줄 대기 큐가 비었을 때") {
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { zSetOperations.range("queue:schedule:1:waiting", 0, -1) } returns setOf("uuid-1")
        every { redisTemplate.hasKey("queue:schedule:1:waiting:heartbeat:uuid-1") } returns false
        every { zSetOperations.remove("queue:schedule:1:waiting", "uuid-1") } returns 1
        every { zSetOperations.size("queue:schedule:1:waiting") } returns 0
        every { setOperations.remove("queue:schedule:waiting", "1") } returns 1

        `when`("비활성 대기자를 제거하면") {
            waitingQueueRedisRepository.removeInactiveWaiting(1L)

            then("스케줄 목록 set에서도 scheduleId를 제거한다") {
                verify(exactly = 1) { setOperations.remove("queue:schedule:waiting", "1") }
            }
        }
    }

    // popWaiting 관련 테스트는 제거했다 — 대기열 승격이 Redis Lua EVAL 하나로 원자화되면서
    // (ActiveQueueRepository.promote, STEP4 선택 심화 ② 보완 ADR-7) WaitingQueueRepository.popWaiting은
    // 더 이상 어떤 프로덕션 코드에서도 호출되지 않아 삭제했다(ADR-3의 AdmissionPolicy 삭제와 같은 원칙).
    // 대기열에서 꺼내고 활성에 등록하는 동작 자체는 QueueTokenLifecycleConcurrencyIntegrationTest(실제
    // Redis)와 ActiveQueueRedisRepository의 Lua 스크립트가 검증한다.
})
