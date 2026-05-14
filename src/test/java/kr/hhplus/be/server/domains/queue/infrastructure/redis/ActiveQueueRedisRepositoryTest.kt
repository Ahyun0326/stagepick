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

class ActiveQueueRedisRepositoryTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val redisTemplate: RedisTemplate<String, Any> = mockk()
    val zSetOperations: ZSetOperations<String, Any> = mockk()
    val setOperations: SetOperations<String, Any> = mockk()
    val activeQueueRedisRepository = ActiveQueueRedisRepository(redisTemplate, QueueProperties())

    given("만료 토큰 정리 후 스케줄 활성 큐가 비었을 때") {
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { zSetOperations.removeRangeByScore("queue:schedule:1:active", 0.0, any()) } returns 1
        every { zSetOperations.size("queue:schedule:1:active") } returns 0
        every { setOperations.remove("queue:schedule:active", "1") } returns 1

        `when`("만료 활성 토큰을 제거하면") {
            activeQueueRedisRepository.removeExpiredActive(1L)

            then("스케줄 목록 set에서도 scheduleId를 제거한다") {
                verify(exactly = 1) { setOperations.remove("queue:schedule:active", "1") }
            }
        }
    }

    given("만료 토큰 정리 후 스케줄 활성 큐가 남아 있을 때") {
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.opsForSet() } returns setOperations
        every { zSetOperations.removeRangeByScore("queue:schedule:1:active", 0.0, any()) } returns 1
        every { zSetOperations.size("queue:schedule:1:active") } returns 1

        `when`("만료 활성 토큰을 제거하면") {
            activeQueueRedisRepository.removeExpiredActive(1L)

            then("스케줄 목록 set에서 scheduleId를 제거하지 않는다") {
                verify(exactly = 0) { setOperations.remove(any(), any()) }
            }
        }
    }
})
