package kr.hhplus.be.server.domains.payment.infrastructure.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
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
//
// reservation_id는 예약당 결제가 하나만 존재해야 하므로 UNIQUE로 변경했다 (STEP4 선택 심화 ②,
// docs/report/step4-concurrency-report.md). 분산락(reservation:{id}:payment)이 동시 결제를
// 직렬화하지만 leaseTime(3초) 고정이라는 구조적 한계가 있어, DB 유니크 제약을 최후 방어선으로 둔다.
// @JoinColumn(unique = true)만 쓰면 Hibernate가 제약 이름을 해시값으로 임의 생성해(예: UK_xxxxxx)
// PaymentJpaRepository.save()에서 "이 제약 위반인지"를 식별할 수 없다. @Table(uniqueConstraints=...)로
// 이름(uk_payment_reservation_id)을 직접 부여하고, @JoinColumn의 unique=true는 중복 선언이라 제거했다
// (같은 컬럼에 이름 없는 UNIQUE 인덱스가 하나 더 생기는 것을 막음 — idx_number와 같은 이유).
@Entity
@Table(
    name = "payment",
    uniqueConstraints = [UniqueConstraint(name = "uk_payment_reservation_id", columnNames = ["reservation_id"])]
)
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