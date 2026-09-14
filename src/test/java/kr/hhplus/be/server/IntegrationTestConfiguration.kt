package kr.hhplus.be.server

import kr.hhplus.be.server.support.DatabaseCleaner
import kr.hhplus.be.server.support.RedisCleaner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import javax.sql.DataSource

@TestConfiguration
class IntegrationTestConfiguration {

    @Bean
    fun databaseCleaner(dataSource: DataSource) = DatabaseCleaner(dataSource)

    @Bean
    fun redisCleaner(redisConnectionFactory: RedisConnectionFactory) = RedisCleaner(redisConnectionFactory)

}