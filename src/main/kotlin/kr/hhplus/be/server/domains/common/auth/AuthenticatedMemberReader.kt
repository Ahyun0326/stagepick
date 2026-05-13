package kr.hhplus.be.server.domains.common.auth

interface AuthenticatedMemberReader {
    fun resolveMemberId(uuid: String): Long
}
