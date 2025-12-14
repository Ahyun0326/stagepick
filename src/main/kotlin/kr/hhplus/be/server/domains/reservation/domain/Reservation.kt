package kr.hhplus.be.server.domains.reservation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.common.BaseEntity
import kr.hhplus.be.server.domains.member.domain.Member

@Entity
@Table(
    name = "reservation", indexes = [
        Index(name = "idx_memeber_id", columnList = "memeber_id")
    ]
)
class Reservation(
    member: Member
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memeber_id")
    var member: Member = member
        protected set

    @Column(nullable = false, unique = true, length = 20)
    var number: String? = null
        protected set
}