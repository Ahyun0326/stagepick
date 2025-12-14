package kr.hhplus.be.server.domains.payment.infrastructure

import kr.hhplus.be.server.domains.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {

}