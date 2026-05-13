package kr.hhplus.be.server.domains.member.infrastructure

import kr.hhplus.be.server.common.exception.MemberNotFoundException
import kr.hhplus.be.server.domains.common.auth.AuthenticatedMemberReader
import kr.hhplus.be.server.domains.member.domain.repository.MemberRepository
import org.springframework.stereotype.Component

@Component
class MemberAuthenticatedMemberReader(
    private val memberRepository: MemberRepository
) : AuthenticatedMemberReader {

    override fun resolveMemberId(uuid: String): Long {
        return memberRepository.findByUuid(uuid)?.id ?: throw MemberNotFoundException()
    }
}
