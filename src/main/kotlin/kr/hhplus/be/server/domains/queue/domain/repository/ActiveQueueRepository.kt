package kr.hhplus.be.server.domains.queue.domain.repository

interface ActiveQueueRepository {
    fun countActive(scheduleId: Long): Long
    fun saveActive(scheduleId: Long, uuid: String): String

    /**
     * 정원(capacity) 미만일 때만 활성 토큰을 발급하는 원자적 연산(Redis Lua).
     * 이미 유효한 활성 토큰이 있으면 그걸 유지한 채 true를 반환하고, 정원이 가득 찼으면 false.
     * countActive 조회 후 saveActive를 호출하는 check-then-act 경합(정원 초과)을 막는다.
     */
    fun tryIssueActive(scheduleId: Long, uuid: String, capacity: Long): Boolean
    fun findActive(scheduleId: Long, uuid: String): String?
    fun getRemainingSeconds(scheduleId: Long, uuid: String): Long
    fun removeActive(scheduleId: Long, uuid: String)
    fun removeExpiredActive(scheduleId: Long)
    fun findScheduleIds(): List<Long>
    fun isValidToken(scheduleId: Long, token: String): Boolean
}
