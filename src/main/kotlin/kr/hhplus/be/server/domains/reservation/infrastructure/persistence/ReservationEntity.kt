package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.hhplus.be.server.common.jpa.BaseEntity

@Entity
@Table(
    name = "reservation",
    indexes = [
        Index(name = "idx_memeber_id", columnList = "memeber_id")
    ]
)
class ReservationEntity(
    memberId: Long,
    number: String
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, name = "memeber_id")
    var memberId: Long = memberId
        protected set

    @Column(nullable = false, unique = true, length = 20)
    var number: String = number
        protected set
}