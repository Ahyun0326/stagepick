package kr.hhplus.be.server.domains.payment.infrastructure.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
import kr.hhplus.be.server.domains.payment.domain.model.PaymentLog
import kr.hhplus.be.server.domains.payment.infrastructure.persistence.dto.PaymentLogDto
import kr.hhplus.be.server.domains.reservation.infrastructure.persistence.ReservationEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

// number 컬럼은 @Column(unique = true)로 이미 UNIQUE 인덱스가 자동 생성된다.
// idx_number는 그 위에 겹친 중복 인덱스였다 — EXPLAIN 실측상 옵티마이저는 항상 UNIQUE 인덱스를 썼고
// (docs/report/step4-index-report.md 시나리오 5), 중복 인덱스는 쓰기 시 유지 비용만 더할 뿐이라 제거했다.
@Entity
@Table(name = "payment", indexes = [
    Index(name = "idx_reservation_id", columnList = "reservation_id")
])
class PaymentEntity(
    reservation: ReservationEntity,
    number: String,
    price: Int,
    status: String,
    paymentLog: PaymentLogDto,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    var reservation: ReservationEntity = reservation
        protected set

    @Column(nullable = false, unique = true, length = 20)
    var number: String = number
        protected set

    @Column(nullable = false)
    var price: Int = price
        protected set

    @Column(nullable = false, length = 20)
    var status: String = status
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    var paymentLog: PaymentLogDto = paymentLog
        protected set

    var paidAt: LocalDateTime? = null
        protected set
}