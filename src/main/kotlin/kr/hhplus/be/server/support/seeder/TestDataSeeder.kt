package kr.hhplus.be.server.support.seeder

import kr.hhplus.be.server.domains.concert.domain.model.Concert
import kr.hhplus.be.server.domains.concert.domain.repository.ConcertRepository
import kr.hhplus.be.server.domains.schedule.domain.model.Schedule
import kr.hhplus.be.server.domains.schedule.domain.repository.ScheduleRepository
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SeatEntity
import kr.hhplus.be.server.domains.seat.infrastructure.persistence.SpringSeatJpa
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate

@Profile("local", "test")
@Component
class TestDataSeeder(
    private val concertRepository: ConcertRepository,
    private val scheduleRepository: ScheduleRepository,
    private val seatRepository: SpringSeatJpa
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        if (concertRepository.count() > 0) return

        val seatsToSave = mutableListOf<SeatEntity>()

        for (i in 1..10) {
            val savedConcert = concertRepository.save(
                Concert(
                    title = "콘서트${i}",
                    location = "장소${i}"
                )
            )

            for (j in 1..2) {
                val concertedAt = LocalDate.now().plusDays(i+j.toLong())
                    .atTime(18, 30)

                val savedSchedule = scheduleRepository.save(
                    Schedule(
                        concert = savedConcert,
                        concertedAt = concertedAt,
                        viewingTime = 2
                    )
                )

                for (k in 1..50) {
                    val seat = SeatEntity(
                        schedule = savedSchedule,
                        number = "S$k",
                        price = 100000
                    )
                    seatsToSave.add(seat)
                }
            }
            seatRepository.saveAll(seatsToSave)
        }
    }

}