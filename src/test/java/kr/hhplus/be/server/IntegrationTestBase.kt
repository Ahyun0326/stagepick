package kr.hhplus.be.server

import kr.hhplus.be.server.support.DatabaseCleaner
import kr.hhplus.be.server.support.RedisCleaner
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("integration")
@Import(IntegrationTestConfiguration::class)
abstract class IntegrationTestBase {
    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var redisCleaner: RedisCleaner

    @BeforeEach
    fun cleanUp() {
        databaseCleaner.clean()
        redisCleaner.clean()
    }
}