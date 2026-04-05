package kr.hhplus.be.server.common.jwt

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RefreshTokenRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    @param:Value("\${spring.jwt.refresh-expiration-ms}") private val refreshExpirationMs: Long,
) {
    fun save(uuid: String, refreshToken: String) {
        redisTemplate.opsForValue().set(
            key(uuid),
            refreshToken,
            refreshExpirationMs,
            TimeUnit.MILLISECONDS
        )
    }

    fun find(uuid: String): String? =
        redisTemplate.opsForValue().get(key(uuid))

    fun delete(uuid: String) {
        redisTemplate.delete(key(uuid))
    }

    private fun key(uuid: String) = "refresh:$uuid"
}