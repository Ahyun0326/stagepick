package kr.hhplus.be.server.domains.point.domain.model

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
import kr.hhplus.be.server.common.jpa.BaseEntity
import kr.hhplus.be.server.domains.member.domain.Member
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(
    name = "point",
    indexes = [
        Index(name = "idx_member_id", columnList = "member_id")
    ]
)
class Point(
    memberId: Long,
    point: Int
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "member_id")
//    @OnDelete(action = OnDeleteAction.CASCADE)
//    var member: Member = member
//        protected set

    var memberId: Long = memberId
        protected set

    @Column(nullable = false)
    var point: Int = point
        protected set

    fun chargePoint(amount: Int): Int {
        point += amount
        return point
    }
}