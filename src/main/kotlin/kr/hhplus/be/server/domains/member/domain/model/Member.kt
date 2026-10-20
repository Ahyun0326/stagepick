package kr.hhplus.be.server.domains.member.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.hhplus.be.server.common.jpa.BaseEntity
import java.util.UUID

// uuid 컬럼은 @Column(unique = true)로 이미 UNIQUE 인덱스가 자동 생성된다.
// idx_uuid는 그 위에 겹친 중복 인덱스였다 — payment.idx_number와 동일한 패턴
// (docs/report/step4-index-report.md 시나리오 6, EXPLAIN 실측상 항상 UNIQUE 인덱스를 썼다) — 제거했다.
@Entity
@Table(name = "member")
class Member(
    loginId: String,
    password: String,
    username: String,
    uuid: String
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, length = 50)
    var loginId: String = loginId
        protected set

    @Column(nullable = false, unique = true, length = 36)
    var uuid: String = uuid
        protected set

    @Column(nullable = false)
    var password: String = password
        protected set

    @Column(nullable = false, length = 50)
    var username: String = username
        protected set

    companion object {
        fun create(loginId: String, encodedPassword: String, username: String): Member {
            return Member(
                loginId,
                encodedPassword,
                username,
                UUID.randomUUID().toString()
            )
        }
    }
}