package kr.hhplus.be.server.domains.reservation.infrastructure.persistence

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kr.hhplus.be.server.IntegrationTestBase
import kr.hhplus.be.server.domains.reservation.domain.model.Reservation
import kr.hhplus.be.server.domains.reservation.domain.repository.ReservationRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("예약 통합 테스트")
class ReservationRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Test
    fun `예약을 저장하면 id가 부여되고 다시 조회된다`() {

        val saved = reservationRepository.save(Reservation(FIXED_NUMBER, MEMBER_ID))
        saved.id shouldNotBe 0L

        val found = reservationRepository.findById(saved.id).shouldNotBeNull()
        found.number shouldBe FIXED_NUMBER
        found.memberId shouldBe MEMBER_ID
    }

    @Test
    fun `격리 검증용 - 위와 같은 번호로 저장해도 성공한다`() {
        val saved = reservationRepository.save(Reservation(FIXED_NUMBER, MEMBER_ID))

        reservationRepository.findById(saved.id)?.number shouldBe FIXED_NUMBER
    }

    companion object {
        private const val FIXED_NUMBER = "test-res-0-01"
        private const val MEMBER_ID = 1L
    }
}
