package kr.hhplus.be.server.domains.concert.domain.model

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
    name = "concert", indexes = [
        Index(name = "idx_title", columnList = "title")]
)
class Concert(
    title: String,
    location: String
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, length = 100)
    var title: String = title
        protected set

    @Column(nullable = false, length = 100)
    var location: String = location
        protected set

}