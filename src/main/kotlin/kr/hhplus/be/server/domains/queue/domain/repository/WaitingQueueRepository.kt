package kr.hhplus.be.server.domains.queue.domain.repository

interface WaitingQueueRepository {
    fun add(uuid: String)
    fun getRank(uuid: String): Long?
    fun popWaiting(count: Long): List<String>
    fun isWaiting(uuid: String): Boolean
}