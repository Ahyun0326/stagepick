package kr.hhplus.be.server.domains.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.common.jpa.BaseEntity

@Entity
@Table(name = "member", indexes = [
    Index(name = "idx_uuid", columnList = "uuid")
])
class Member : BaseEntity() {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
    
    @Column(nullable = false, unique = true, length = 50)
    var loginId: String? = null
        protected set

    @Column(nullable = false, unique = true, length = 20)
    var uuid: String? = null
        protected set

    @Column(nullable = false)
    var password: String? = null
        protected set

    @Column(nullable = false, length = 50)
    var username: String? = null
        protected set
}