package kr.hhplus.be.server.domains.member.infrastructure

import kr.hhplus.be.server.domains.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
}