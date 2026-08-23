package com.travel_plan.travel_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.travel_service.domain.Report;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ReportRepositoryTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private TravelRepository travelRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void listsReportsForATravel() {
        Travel travel = travelRepository.save(newTravel());
        reportRepository.save(newReport(travel));
        entityManager.flush();
        entityManager.clear();

        assertThat(reportRepository.findByTravel_Id(travel.getId())).hasSize(1);
    }

    @Test
    void listsAllReportsNewestFirst() {
        Travel travel = travelRepository.save(newTravel());
        // Deux timestamps explicitement distincts plutot qu'une vraie attente (Thread.sleep) :
        // ordre deterministe garanti, test instantane. Voir troubleshooting.md.
        Instant baseInstant = Instant.parse("2026-06-15T10:00:00Z");
        Report older = reportRepository.save(newReport(travel, baseInstant));
        Report newer = reportRepository.save(newReport(travel, baseInstant.plusSeconds(1)));
        entityManager.flush();
        entityManager.clear();

        assertThat(reportRepository.findAllByOrderByCreatedAtDesc())
                .extracting(Report::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    private Travel newTravel() {
        return Travel.builder()
                .title("Iberian tour")
                .managerId(UUID.randomUUID())
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 10))
                .status(TravelStatus.PLANNED)
                .build();
    }

    private Report newReport(Travel travel) {
        return newReport(travel, Instant.now(Clock.systemUTC()));
    }

    private Report newReport(Travel travel, Instant createdAt) {
        return Report.builder()
                .travel(travel)
                .reporterId(UUID.randomUUID())
                .reportedType(ReportedType.MANAGER)
                .reportedId(travel.getManagerId())
                .reason("reason")
                .createdAt(createdAt)
                .build();
    }
}
