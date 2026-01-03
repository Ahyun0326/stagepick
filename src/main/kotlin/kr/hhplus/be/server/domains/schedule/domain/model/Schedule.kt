package kr.hhplus.be.server.domains.schedule.domain.model

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
import kr.hhplus.be.server.domains.concert.domain.model.Concert
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDateTime

@Entity
@Table(
    name = "schedules", indexes = [
        Index(
            name = "idx_concert_id_concerted_at",
            columnList = "concert_id, concerted_at"
        )]
)
class Schedule(
    concert: Concert,
    concertedAt: LocalDateTime,
    viewingTime: Int
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var concert: Concert = concert
        protected set

    @Column(name = "concerted_at", nullable = false)
    var concertedAt: LocalDateTime = concertedAt
        protected set

    @Column(name = "viewing_time", nullable = false)
    var viewingTime: Int = viewingTime
        protected set
}