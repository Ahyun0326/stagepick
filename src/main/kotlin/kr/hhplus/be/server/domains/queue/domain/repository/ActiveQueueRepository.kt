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

    /**
     * 대기열 승격을 원자적으로 수행한다(STEP4 선택 심화 ② 보완, ADR-7).
     * "남은 활성 슬롯 계산(countActive 상당) → 대기열에서 꺼내기(popWaiting 상당) →
     * 활성 등록(saveActive 상당)"을 Redis Lua EVAL 하나로 묶어, 멀티 인스턴스가 동시에
     * 같은 스케줄을 승격시켜도 check-then-act 경합 없이 정원을 넘지 않는다.
     *
     * Lua 스크립트 안에서는 새 활성 토큰(UUID)을 생성할 수 없어, 이번 tick에서 최대로
     * 승격될 수 있는 인원(admissionRatePerTick)만큼 [candidateTokens]를 호출 전에 미리
     * 만들어 전달한다. 실제로 승격되는 인원이 더 적으면 남는 후보는 버려진다.
     *
     * @return 이번 호출로 실제 승격된 대기자 uuid 목록(승격이 없으면 빈 리스트)
     */
    fun promote(
        scheduleId: Long,
        capacity: Long,
        admissionRatePerTick: Long,
        candidateTokens: List<String>
    ): List<String>

    fun findActive(scheduleId: Long, uuid: String): String?
    fun getRemainingSeconds(scheduleId: Long, uuid: String): Long
    fun removeActive(scheduleId: Long, uuid: String)
    fun removeExpiredActive(scheduleId: Long)
    fun findScheduleIds(): List<Long>
    fun isValidToken(scheduleId: Long, token: String): Boolean
}
