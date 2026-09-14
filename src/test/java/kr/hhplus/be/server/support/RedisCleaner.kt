package kr.hhplus.be.server.support

import org.springframework.data.redis.connection.RedisConnectionFactory

class RedisCleaner(
    private val redisConnectionFactory: RedisConnectionFactory
) {
    fun clean() {
        redisConnectionFactory.connection.use { connection ->
            connection.serverCommands().flushDb()
        }
    }
}