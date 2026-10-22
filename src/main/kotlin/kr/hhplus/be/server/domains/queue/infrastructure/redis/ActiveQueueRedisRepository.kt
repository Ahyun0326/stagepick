package kr.hhplus.be.server.domains.queue.infrastructure.redis

import kr.hhplus.be.server.domains.queue.domain.repository.ActiveQueueRepository
import kr.hhplus.be.server.domains.queue.infrastructure.config.QueueProperties
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.TimeUnit

@Repository
class ActiveQueueRedisRepository(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val queueProperties: QueueProperties
) : ActiveQueueRepository {

    companion object {
        // 정원 확인(ZCOUNT)과 등록(SADD+ZADD+SET)을 한 번에 실행 → countActive 후 saveActive를
        // 따로 호출하던 check-then-act 경합(정원 초과 발급)을 막는다.
        // KEYS[1] = 스케줄 활성 ZSET, KEYS[2] = uuid -> token 매핑 key, KEYS[3] = 활성 스케줄 집합
        // ARGV[1] = 현재 시각(ms), ARGV[2] = 정원, ARGV[3] = 새로 발급할 토큰,
        // ARGV[4] = 만료 시각(ms), ARGV[5] = TTL(ms), ARGV[6] = scheduleId(문자열)
        // 반환: 1 = 활성 보장(기존 토큰 유지 또는 신규 발급), 0 = 정원 초과로 미발급
        // 같은 uuid로 동시에 여러 번 호출돼도 Redis가 EVAL을 한 번에 하나씩만 실행하므로,
        // 먼저 실행된 호출이 심어둔 uuid 매핑을 뒤이은 호출이 그대로 재사용해 토큰이 중복 집계되지 않는다.
        private val TRY_ISSUE_ACTIVE_SCRIPT = DefaultRedisScript(
            """
            local existingToken = redis.call('GET', KEYS[2])
            if existingToken then
                local score = redis.call('ZSCORE', KEYS[1], existingToken)
                if score and tonumber(score) > tonumber(ARGV[1]) then
                    return 1
                end
            end

            local activeCount = redis.call('ZCOUNT', KEYS[1], ARGV[1], '+inf')
            if tonumber(activeCount) < tonumber(ARGV[2]) then
                redis.call('SADD', KEYS[3], ARGV[6])
                redis.call('ZADD', KEYS[1], ARGV[4], ARGV[3])
                redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[5])
                return 1
            end

            return 0
            """.trimIndent(),
            Long::class.java
        )

        // 대기열 승격 원자화(STEP4 선택 심화 ② 보완, ADR-7).
        // "남은 활성 슬롯 계산(ZCOUNT) → 대기열에서 꺼내기(ZPOPMIN) → 활성 등록(SADD+ZADD+SET+하트비트 정리)"을
        // 한 번의 EVAL로 묶어, 여러 인스턴스가 동시에 같은 스케줄을 승격시켜도 check-then-act 경합
        // 없이 정원을 넘지 않는다.
        //
        // KEYS[1] = 스케줄 활성 ZSET, KEYS[2] = 활성 스케줄 집합,
        // KEYS[3] = 스케줄 대기열 ZSET, KEYS[4] = 대기 스케줄 집합
        // ARGV[1] = 현재 시각(ms), ARGV[2] = 정원, ARGV[3] = tick당 입장 허용 수,
        // ARGV[4] = 활성 토큰 TTL(ms), ARGV[5] = scheduleId,
        // ARGV[6..] = 이번 tick에서 승격될 수 있는 최대 인원(admissionRatePerTick)만큼 앱이 미리 만들어
        //             전달하는 신규 활성 토큰 후보(UUID는 Lua 안에서 만들 수 없어 바깥에서 준비해 넘긴다)
        //
        // [주의] RedisTemplate<String, Any>의 값 직렬화는 GenericJackson2JsonRedisSerializer라서, ARGV로
        // 넘긴 "문자열" 값은 Lua 안에서 앞뒤에 쌍따옴표가 그대로 붙은 바이트로 보인다(예: "abc-123"이 아니라
        // 문자 그대로 \"abc-123\"). ZPOPMIN으로 꺼낸 대기자 uuid도 마찬가지로 쌍따옴표가 붙은 채로 온다.
        // 이 스크립트는 그 uuid를 그대로 복사하는 게 아니라 "queue:schedule:{id}:active:uuid:{uuid}" 같은
        // 새 키 이름을 직접 만들어야 해서, Redis Lua 내장 cjson.decode로 이 바이트를 JSON 문자열로
        // 파싱해 평문 uuid를 복원한다 — JSON 디코딩이라 이스케이프 유무와 무관하게 항상 올바른 평문을
        // 돌려준다(이전에는 string.sub으로 앞뒤 쌍따옴표만 잘라내 "uuid에 JSON 이스케이프가 필요한
        // 문자가 없다"는 전제에 의존했는데, cjson.decode로 바꿔 그 전제 자체를 없앴다). scheduleId(ARGV[5])는
        // 반대로 숫자로 넘겨 키 이름 조립에는 그대로 쓰고, SADD/SREM 멤버로 넣을 때만 쌍따옴표로 다시
        // 감싸(Kotlin이 scheduleId.toString()을 저장할 때와 같은 바이트가 되도록) 기존 스케줄 집합
        // 포맷과 맞춘다.
        @Suppress("UNCHECKED_CAST")
        private val PROMOTE_SCRIPT = DefaultRedisScript(
            """
            local activeCount = tonumber(redis.call('ZCOUNT', KEYS[1], ARGV[1], '+inf'))
            local remaining = tonumber(ARGV[2]) - activeCount
            local rate = tonumber(ARGV[3])
            local n = remaining
            if rate < n then
                n = rate
            end
            if n <= 0 then
                return {}
            end

            local popped = redis.call('ZPOPMIN', KEYS[3], n)
            if popped == nil or #popped == 0 then
                return {}
            end

            local scheduleIdText = ARGV[5]
            local scheduleIdMember = '"' .. scheduleIdText .. '"'
            local expireAt = tonumber(ARGV[1]) + tonumber(ARGV[4])
            local promoted = {}
            local tokenIndex = 6

            for i = 1, #popped, 2 do
                local rawUuid = popped[i]
                local uuid = cjson.decode(rawUuid)
                local token = ARGV[tokenIndex]
                tokenIndex = tokenIndex + 1

                redis.call('SADD', KEYS[2], scheduleIdMember)
                redis.call('ZADD', KEYS[1], expireAt, token)
                redis.call('SET', 'queue:schedule:' .. scheduleIdText .. ':active:uuid:' .. uuid, token, 'PX', ARGV[4])
                redis.call('DEL', 'queue:schedule:' .. scheduleIdText .. ':waiting:heartbeat:' .. uuid)

                table.insert(promoted, rawUuid)
            end

            if redis.call('ZCARD', KEYS[3]) == 0 then
                redis.call('SREM', KEYS[4], scheduleIdMember)
            end

            return promoted
            """.trimIndent(),
            List::class.java as Class<List<Any>>
        )
    }

    override fun countActive(scheduleId: Long): Long {
        val now = System.currentTimeMillis()

        // 남은 활성 인원수 반환
        return redisTemplate.opsForZSet().count(activeKey(scheduleId), now.toDouble(), Double.MAX_VALUE) ?: 0L
    }

    /**
     * 활성 토큰 생성
     */
    override fun saveActive(scheduleId: Long, uuid: String): String {
        val key = activeUuidKey(scheduleId, uuid)
        val expirationTime = System.currentTimeMillis() + queueProperties.activeTokenTtlMs

        // 토큰 생성
        val token = UUID.randomUUID().toString()

        // ZSET에 token 추가
        redisTemplate.opsForSet().add(QueueRedisKeys.ACTIVE_SCHEDULE_SET_KEY, scheduleId.toString())
        redisTemplate.opsForZSet().add(activeKey(scheduleId), token, expirationTime.toDouble())

        // uuid -> token 매핑 저장
        redisTemplate.opsForValue().set(key, token, queueProperties.activeTokenTtlMs, TimeUnit.MILLISECONDS)

        return token
    }

    override fun tryIssueActive(scheduleId: Long, uuid: String, capacity: Long): Boolean {
        val now = System.currentTimeMillis()
        val token = UUID.randomUUID().toString()
        val expirationTime = now + queueProperties.activeTokenTtlMs

        val admitted = redisTemplate.execute(
            TRY_ISSUE_ACTIVE_SCRIPT,
            listOf(activeKey(scheduleId), activeUuidKey(scheduleId, uuid), QueueRedisKeys.ACTIVE_SCHEDULE_SET_KEY),
            now,
            capacity,
            token,
            expirationTime,
            queueProperties.activeTokenTtlMs,
            scheduleId.toString()
        )

        return admitted == 1L
    }

    override fun promote(
        scheduleId: Long,
        capacity: Long,
        admissionRatePerTick: Long,
        candidateTokens: List<String>
    ): List<String> {
        val now = System.currentTimeMillis()
        val keys = listOf(
            activeKey(scheduleId),
            QueueRedisKeys.ACTIVE_SCHEDULE_SET_KEY,
            QueueRedisKeys.waitingKey(scheduleId),
            QueueRedisKeys.WAITING_SCHEDULE_SET_KEY
        )
        val args = buildList<Any> {
            add(now)
            add(capacity)
            add(admissionRatePerTick)
            add(queueProperties.activeTokenTtlMs)
            add(scheduleId)
            addAll(candidateTokens)
        }

        val promoted = redisTemplate.execute(PROMOTE_SCRIPT, keys, *args.toTypedArray())

        return promoted?.mapNotNull { it as? String } ?: emptyList()
    }

    override fun findActive(scheduleId: Long, uuid: String): String? =
        redisTemplate.opsForValue().get(activeUuidKey(scheduleId, uuid)) as? String

    override fun getRemainingSeconds(scheduleId: Long, uuid: String): Long {
        val token = findActive(scheduleId, uuid) ?: return 0L
        val expirationTime = redisTemplate.opsForZSet().score(activeKey(scheduleId), token)
            ?: return 0L

        return ((expirationTime - System.currentTimeMillis()) / 1000).toLong()
    }

    override fun removeActive(scheduleId: Long, uuid: String) {
        val key = activeKey(scheduleId)
        val uuidKey = activeUuidKey(scheduleId, uuid)
        val token = redisTemplate.opsForValue().get(uuidKey) as? String ?: return

        redisTemplate.opsForZSet().remove(key, token)
        redisTemplate.delete(uuidKey)
        removeScheduleIdIfEmpty(scheduleId, key)
    }

    override fun removeExpiredActive(scheduleId: Long) {
        val key = activeKey(scheduleId)
        val now = System.currentTimeMillis().toDouble()

        redisTemplate.opsForZSet().removeRangeByScore(key, 0.0, now)
        removeScheduleIdIfEmpty(scheduleId, key)
    }

    override fun findScheduleIds(): List<Long> {
        return redisTemplate.opsForSet().members(QueueRedisKeys.ACTIVE_SCHEDULE_SET_KEY)
            ?.mapNotNull { it.toString().toLongOrNull() }
            ?: emptyList()
    }

    override fun isValidToken(scheduleId: Long, token: String): Boolean {
        val score = redisTemplate.opsForZSet().score(activeKey(scheduleId), token) ?: return false
        return score > System.currentTimeMillis()
    }

    private fun activeKey(scheduleId: Long): String = QueueRedisKeys.activeKey(scheduleId)

    private fun activeUuidKey(scheduleId: Long, uuid: String): String =
        QueueRedisKeys.activeUuidKey(scheduleId, uuid)

    private fun removeScheduleIdIfEmpty(scheduleId: Long, key: String) {
        val size = redisTemplate.opsForZSet().size(key) ?: 0L
        if (size == 0L) {
            redisTemplate.opsForSet().remove(QueueRedisKeys.ACTIVE_SCHEDULE_SET_KEY, scheduleId.toString())
        }
    }
}
