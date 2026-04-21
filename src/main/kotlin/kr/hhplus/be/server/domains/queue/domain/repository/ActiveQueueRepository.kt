package kr.hhplus.be.server.domains.queue.domain.repository

interface ActiveQueueRepository {
    fun countActive(): Long
    fun saveActive(uuid: String): String
    fun findActive(uuid: String): String?
    fun getRemainingSeconds(uuid: String): Long
    fun removeExpiredActive()
    fun isValidToken(token: String): Boolean
}