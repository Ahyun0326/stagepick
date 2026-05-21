package kr.hhplus.be.server.domains.queue.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "queue")
class QueueProperties {
    var capacity: Long = 1000
    var admissionRatePerTick: Long = 30
    var schedulerIntervalMs: Long = 3_000
    var activeTokenTtlMs: Long = 5 * 60 * 1000L
    var waitingHeartbeatTtlMs: Long = 60 * 1000L
}
