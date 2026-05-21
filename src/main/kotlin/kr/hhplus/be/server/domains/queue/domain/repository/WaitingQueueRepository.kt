package kr.hhplus.be.server.domains.queue.domain.repository

interface WaitingQueueRepository {
    fun add(scheduleId: Long, uuid: String)
    fun getRank(scheduleId: Long, uuid: String): Long?
    fun popWaiting(scheduleId: Long, count: Long): List<String>
    fun isWaiting(scheduleId: Long, uuid: String): Boolean
    fun touchHeartbeat(scheduleId: Long, uuid: String)
    fun removeInactiveWaiting(scheduleId: Long)
    fun findScheduleIds(): List<Long>
}
