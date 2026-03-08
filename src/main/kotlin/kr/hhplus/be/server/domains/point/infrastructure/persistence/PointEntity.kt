package kr.hhplus.be.server.domains.point.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.common.jpa.BaseEntity
import kr.hhplus.be.server.domains.point.domain.model.Point

@Entity
@Table(
    name = "point",
    indexes = [
        Index(name = "idx_member_id", columnList = "member_id")
    ]
)
class PointEntity(
    memberId: Long,
    point: Int
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var memberId: Long = memberId
        protected set

    @Column(nullable = false)
    var point: Int = point
        protected set

    fun updateFrom(domain: Point) {
        point = domain.point
    }
}
