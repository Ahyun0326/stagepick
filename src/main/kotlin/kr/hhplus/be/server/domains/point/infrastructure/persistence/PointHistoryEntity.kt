package kr.hhplus.be.server.domains.point.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.common.jpa.BaseEntity
import kr.hhplus.be.server.domains.point.domain.model.PointHistory

@Entity
@Table(
    name = "point_history",
    indexes = [
        Index(
            name = "idx_member_id_created_at",
            columnList = "member_id, created_at"
        )
    ]
)
class PointHistoryEntity(
    memberId: Long,
    currentPoint: Int,
    changedPoint: Int,
    type: String
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var memberId: Long = memberId
        protected set

    @Column(nullable = false)
    var currentPoint: Int = currentPoint
        protected set

    @Column(nullable = false)
    var changedPoint: Int = changedPoint
        protected set

    @Column(nullable = false, length = 10)
    var type: String = type
        protected set
}
