package kr.hhplus.be.server.domains.member.domain.repository

import kr.hhplus.be.server.domains.member.domain.model.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByLoginId(loginId: String) : Member?
}