package kr.hhplus.be.server.domains.member.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.common.jpa.BaseEntity
import java.util.UUID

@Entity
@Table(name = "member", indexes = [
    Index(name = "idx_uuid", columnList = "uuid")
])
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