package kr.hhplus.be.server.domains.payment.infrastructure.persistence

import kr.hhplus.be.server.common.exception.DuplicatePaymentException
import kr.hhplus.be.server.domains.payment.domain.model.Payment
import kr.hhplus.be.server.domains.payment.domain.repository.PaymentRepository
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.infrastructure.persistence.SpringReservationJpa
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class PaymentJpaRepository(
    private val springPaymentJpa: SpringPaymentJpa,
    private val springReservationJpa: SpringReservationJpa,
    private val paymentMapper: PaymentMapper,
): PaymentRepository {

    override fun findByReservationId(reservationId: Long): Payment? {
        return springPaymentJpa.findByReservationId(reservationId)
            .map { paymentMapper.toDomain(it) }
            .orElse(null)
    }

    override fun save(payment: Payment, reservation: Reservation): Payment {
        val reservationEntity = springReservationJpa.getReferenceById(reservation.id)
        val paymentEntity = paymentMapper.toEntity(payment, reservationEntity)

        // reservation_id UNIQUE 제약(uk_payment_reservation_id) 위반 → 분산락 기반의 중복 결제 검증을
        // 뚫고 들어온 경우의 최후 방어선. (예: 락 leaseTime 만료로 두 트랜잭션이 겹치는 구조적 가능성 —
        // 재현은 못했지만 방어는 유지)
        //
        // DataIntegrityViolationException은 FK 위반, NOT NULL 위반 등 모든 무결성 위반을 뭉뚱그려
        // 던지므로, 그 원인이 "이" UNIQUE 제약 위반일 때만 DuplicatePaymentException으로 변환하고
        // 그 외(다른 제약 위반 등)는 그대로 전파한다 — 원인을 숨기지 않고 로그/장애 분석에서 실제
        // 문제를 드러내기 위함.
        //
        // 실측(Testcontainers MySQL 8.0): 원인 예외는 org.hibernate.exception.ConstraintViolationException
        // 이고, 그 constraintName은 "payment.uk_payment_reservation_id"처럼 테이블명이 접두사로 붙는다
        // (MySQL이 "Duplicate entry 'X' for key 'payment.uk_payment_reservation_id'" 형식으로 반환).
        // 접두사 포맷은 MySQL 버전/설정에 따라 달라질 수 있어, 정확히 일치하는 대신 제약 이름이
        // 포함되는지(contains)로 판단한다.
        return try {
            paymentMapper.toDomain(springPaymentJpa.save(paymentEntity))
        } catch (e: DataIntegrityViolationException) {
            val cause = e.cause
            if (cause is ConstraintViolationException && cause.constraintName?.contains(RESERVATION_ID_UNIQUE_CONSTRAINT_NAME) == true) {
                throw DuplicatePaymentException()
            }
            throw e
        }
    }

    companion object {
        private const val RESERVATION_ID_UNIQUE_CONSTRAINT_NAME = "uk_payment_reservation_id"
    }
}