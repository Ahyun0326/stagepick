package kr.hhplus.be.server.domains.queue.infrastructure.redis

/**
 * 대기열(active/waiting) Redis 키 포맷을 한곳에 모아둔다.
 *
 * [ActiveQueueRedisRepository.promote] (STEP4 ADR-7)가 활성 ZSET·uuid 매핑 키뿐 아니라
 * 대기열(waiting) ZSET·heartbeat 키까지 하나의 Lua EVAL 안에서 함께 다뤄야 해서, 두 저장소
 * ([ActiveQueueRedisRepository], [WaitingQueueRedisRepository])가 같은 키 포맷 문자열을
 * 공유해야 한다. 포맷이 한쪽에서만 바뀌고 다른 쪽이 따라가지 못하면 Lua 스크립트가 조용히
 * 다른 키를 건드리게 되므로, 포맷을 이 객체 하나로 모아 drift를 막는다.
 */
internal object QueueRedisKeys {
    private const val ACTIVE_ZSET_KEY_PATTERN = "queue:schedule:%d:active"
    private const val ACTIVE_UUID_KEY_PATTERN = "queue:schedule:%d:active:uuid:%s"
    const val ACTIVE_SCHEDULE_SET_KEY = "queue:schedule:active"

    private const val WAITING_ZSET_KEY_PATTERN = "queue:schedule:%d:waiting"
    private const val WAITING_HEARTBEAT_KEY_PATTERN = "queue:schedule:%d:waiting:heartbeat:%s"
    const val WAITING_SCHEDULE_SET_KEY = "queue:schedule:waiting"

    fun activeKey(scheduleId: Long): String = String.format(ACTIVE_ZSET_KEY_PATTERN, scheduleId)

    fun activeUuidKey(scheduleId: Long, uuid: String): String =
        String.format(ACTIVE_UUID_KEY_PATTERN, scheduleId, uuid)

    fun waitingKey(scheduleId: Long): String = String.format(WAITING_ZSET_KEY_PATTERN, scheduleId)

    fun waitingHeartbeatKey(scheduleId: Long, uuid: String): String =
        String.format(WAITING_HEARTBEAT_KEY_PATTERN, scheduleId, uuid)
}
