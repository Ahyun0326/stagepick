package kr.hhplus.be.server.domains.queue.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "queue")
class QueueProperties {
    var capacity: Long = 1000
    var admissionRatePerTick: Long = 30
    var schedulerIntervalMs: Long = 3_000
    var bookingTokenTtlMs: Long = 30 * 60 * 1000L
    var waitingTokenTtlMs: Long = 10 * 60 * 1000L
}
