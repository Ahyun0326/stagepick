package kr.hhplus.be.server.domains.queue.infrastructure.redis

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ZSetOperations

class WaitingQueueRedisRepositoryTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val redisTemplate: RedisTemplate<String, Any> = mockk()
    val zSetOperations: ZSetOperations<String, Any> = mockk()
    val setOperations: SetOperations<String, Any> = mockk()
    val waitingQueueRedisRepository = WaitingQueueRedisRepository(redisTemplate, QueueProperties())

    given("만료 엔트리 정리 후 스케줄 대기 큐가 비었을 때") {
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { zSetOperations.removeRangeByScore("queue:schedule:1:waiting", 0.0, any()) } returns 1
        every { zSetOperations.size("queue:schedule:1:waiting") } returns 0
        every { setOperations.remove("queue:schedule:waiting", "1") } returns 1

        `when`("만료 대기 엔트리를 제거하면") {
            waitingQueueRedisRepository.removeExpiredWaiting(1L)

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
})
