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

    given("대기자를 승격한 후 스케줄 대기 큐가 비었을 때") {
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { zSetOperations.popMin("queue:schedule:1:waiting", 10) } returns emptySet()
        every { zSetOperations.size("queue:schedule:1:waiting") } returns 0
        every { setOperations.remove("queue:schedule:waiting", "1") } returns 1

        `when`("대기열에서 승격 대상자를 꺼내면") {
            waitingQueueRedisRepository.popWaiting(1L, 10)

            then("스케줄 목록 set에서도 scheduleId를 제거한다") {
                verify(exactly = 1) { setOperations.remove("queue:schedule:waiting", "1") }
            }
        }
    }

    given("대기자를 승격했을 때") {
        val tuple: ZSetOperations.TypedTuple<Any> = mockk()

        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { tuple.value } returns "member-uuid"
        every { zSetOperations.popMin("queue:schedule:1:waiting", 1) } returns setOf(tuple)
        every {
            redisTemplate.delete(listOf("queue:schedule:1:waiting:heartbeat:member-uuid"))
        } returns 1
        every { zSetOperations.size("queue:schedule:1:waiting") } returns 1

        `when`("대기열에서 승격 대상자를 꺼내면") {
            waitingQueueRedisRepository.popWaiting(1L, 1)

            then("승격된 사용자의 heartbeat key를 제거한다") {
                verify(exactly = 1) {
                    redisTemplate.delete(listOf("queue:schedule:1:waiting:heartbeat:member-uuid"))
                }
            }
        }
    }
})
