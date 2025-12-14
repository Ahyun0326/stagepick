package kr.hhplus.be.server.domains.point.domain

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
    name = "point_history", indexes = [
        Index(
            name = "idx_member_id_created_at",
            columnList = "member_id, created_at"
        )]
)
class PointHistory(
    member: Member
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    var member: Member = member
        protected set

    @Column(nullable = false)
    var currentPoint: Int? = null
        protected set

    @Column(nullable = false)
    var chargedPoint: Int? = null
        protected set

    @Column(nullable = false, length = 10)
    var type: String? = null
        protected set
}