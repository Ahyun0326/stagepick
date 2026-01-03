package kr.hhplus.be.server.domains.seat.infrastructure.persistence

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
import jakarta.persistence.UniqueConstraint
import kr.hhplus.be.server.common.jpa.BaseEntity
import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import kr.hhplus.be.server.domains.seat.domain.model.Seat
import kr.hhplus.be.server.domains.seat.domain.model.SeatStatus
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(
    name = "seat",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_schedule_id_number",
            columnNames = ["schedule_id", "number"]
        )
    ],
    indexes = [
        Index(name = "idx_reservation_id", columnList = "reservation_id")
    ]
)
class SeatEntity(
    schedule: Schedule,
    number: String,
    status: String = SeatStatus.AVAILABLE.name,
    price: Int
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var schedule: Schedule = schedule
        protected set

    @Column(name = "reservation_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var reservationId: Long? = null
        protected set

    @Column(nullable = false, length = 10)
    var number: String = number
        protected set

    @Column(nullable = false, length = 20)
    var status: String = status
        protected set

    @Column(nullable = false)
    var price: Int = price
        protected set

    fun updateFrom(seat: Seat) {
        status = seat.status
        reservationId = seat.reservationId
    }
}

