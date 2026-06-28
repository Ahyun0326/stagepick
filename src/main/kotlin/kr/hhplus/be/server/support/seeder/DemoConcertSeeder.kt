package kr.hhplus.be.server.support.seeder

import kr.hhplus.be.server.domains.concert.domain.model.Concert
import kr.hhplus.be.server.domains.concert.domain.repository.ConcertRepository
import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SeatEntity
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SpringSeatJpa
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate

@Profile("prod")
@ConditionalOnProperty(
    prefix = "app.demo-seed",
    name = ["enabled"],
    havingValue = "true"
)
@Component
class DemoConcertSeeder(
    private val concertRepository: ConcertRepository,
    private val scheduleRepository: ScheduleRepository,
    private val seatRepository: SpringSeatJpa
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        if (concertRepository.count() > 0) return

        demoConcerts.forEachIndexed { index, seed ->
            val savedConcert = concertRepository.save(
                Concert(
                    title = seed.title,
                    location = seed.location
                )
            )

            repeat(SCHEDULES_PER_CONCERT) { scheduleIndex ->
                val concertedAt = LocalDate.now()
                    .plusDays((index + 1L) * 2 + scheduleIndex)
                    .atTime(18, 30)

                val savedSchedule = scheduleRepository.save(
                    Schedule(
                        concert = savedConcert,
                        concertedAt = concertedAt,
                        viewingTime = DEFAULT_VIEWING_TIME
                    )
                )

                val seatsToSave = (1..SEATS_PER_SCHEDULE).map { seatNumber ->
                    SeatEntity(
                        schedule = savedSchedule,
                        number = "S$seatNumber",
                        price = seed.priceOf(seatNumber)
                    )
                }

                seatRepository.saveAll(seatsToSave)
            }
        }
    }

    private data class DemoConcertSeed(
        val title: String,
        val location: String,
        val basePrice: Int
    ) {
        fun priceOf(seatNumber: Int): Int {
            return when (seatNumber) {
                in 1..10 -> basePrice + 50_000
                in 11..30 -> basePrice + 20_000
                else -> basePrice
            }
        }
    }

    companion object {
        private const val SCHEDULES_PER_CONCERT = 2
        private const val SEATS_PER_SCHEDULE = 50
        private const val DEFAULT_VIEWING_TIME = 2

        private val demoConcerts = listOf(
            DemoConcertSeed("스테이지 원 라이브", "올림픽공원 KSPO DOME", 132_000),
            DemoConcertSeed("한여름 밤의 사운드", "잠실실내체육관", 121_000),
            DemoConcertSeed("시티팝 나이트", "블루스퀘어 마스터카드홀", 99_000),
            DemoConcertSeed("인디 웨이브 페스티벌", "예스24 라이브홀", 88_000),
            DemoConcertSeed("오로라 월드 투어", "고척스카이돔", 154_000),
            DemoConcertSeed("재즈 온 더 리버", "세종문화회관 대극장", 110_000),
            DemoConcertSeed("락 더 스테이지", "인천문학경기장", 143_000),
            DemoConcertSeed("어쿠스틱 데이", "노들섬 라이브하우스", 77_000),
            DemoConcertSeed("문라이트 콘서트", "LG아트센터 서울", 118_000),
            DemoConcertSeed("사운드 플래닛", "올림픽홀", 126_000),
            DemoConcertSeed("레트로 비트 쇼", "장충체육관", 99_000),
            DemoConcertSeed("싱어송라이터스", "홍대 무신사 개러지", 66_000),
            DemoConcertSeed("일렉트릭 유니버스", "킨텍스 제1전시장", 139_000),
            DemoConcertSeed("발라드 포 시즌", "경희대학교 평화의전당", 121_000),
            DemoConcertSeed("그랜드 오케스트라", "예술의전당 콘서트홀", 132_000),
            DemoConcertSeed("힙합 라운지", "YES24 원더로크홀", 88_000),
            DemoConcertSeed("봄의 멜로디", "연세대학교 백주년기념관", 99_000),
            DemoConcertSeed("더 라이징 밴드", "롤링홀", 55_000),
            DemoConcertSeed("케이팝 스타라이트", "잠실종합운동장 보조경기장", 165_000),
            DemoConcertSeed("피아노 앤 스트링스", "롯데콘서트홀", 118_000),
            DemoConcertSeed("블루밍 보이스", "코엑스 신한카드 아티움", 99_000),
            DemoConcertSeed("디바 나이트", "이화여자대학교 삼성홀", 110_000),
            DemoConcertSeed("더 뮤직 캠프", "서울숲 야외무대", 77_000),
            DemoConcertSeed("클래식 브리즈", "성남아트센터 콘서트홀", 99_000),
            DemoConcertSeed("서머 라우드", "난지한강공원", 132_000),
            DemoConcertSeed("감성 플레이리스트", "KT&G 상상마당 라이브홀", 66_000),
            DemoConcertSeed("퓨처 사운드", "동대문디자인플라자", 121_000),
            DemoConcertSeed("올나잇 그루브", "워커힐 씨어터", 143_000),
            DemoConcertSeed("뮤직 포레스트", "서울어린이대공원 숲속의무대", 88_000),
            DemoConcertSeed("파이널 앙코르", "올림픽공원 체조경기장", 154_000)
        )
    }
}
