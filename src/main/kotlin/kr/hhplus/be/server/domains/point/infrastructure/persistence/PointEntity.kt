package kr.hhplus.be.server.domains.point.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.hhplus.be.server.common.jpa.BaseEntity
import kr.hhplus.be.server.domains.point.domain.model.Point

// member_id는 회원당 포인트 행이 하나만 존재해야 한다 (findByMemberId가 단건을 전제).
// UNIQUE로 바꿔 최초 충전 동시 요청 시 중복 행이 생기는 것을 DB가 막는다 (STEP4 선택 심화 ②,
// docs/report/step4-concurrency-report.md). UNIQUE 제약이 만드는 인덱스가 기존 idx_member_id와
// 같은 컬럼을 중복으로 덮으므로, payment.idx_number와 같은 이유로 별도 Index 선언은 제거했다.
@Entity
@Table(name = "point")
class PointEntity(
    memberId: Long,
    point: Int
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, unique = true)
    var memberId: Long = memberId
        protected set

    @Column(nullable = false)
    var point: Int = point
        protected set

    fun updateFrom(domain: Point) {
        point = domain.point
    }
}
