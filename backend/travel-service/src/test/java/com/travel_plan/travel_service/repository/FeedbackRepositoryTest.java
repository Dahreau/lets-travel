package com.travel_plan.travel_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.travel_service.domain.Feedback;
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
class FeedbackRepositoryTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TravelRepository travelRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsFeedbackForTravelAndTraveler() {
        Travel travel = travelRepository.save(newTravel());
        UUID travelerId = UUID.randomUUID();
        feedbackRepository.save(newFeedback(travel, travelerId));
        entityManager.flush();
        entityManager.clear();

        assertThat(feedbackRepository.findByTravel_IdAndTravelerId(travel.getId(), travelerId)).isPresent();
    }

    @Test
    void listsAllFeedbackForATravel() {
        Travel travel = travelRepository.save(newTravel());
        feedbackRepository.save(newFeedback(travel, UUID.randomUUID()));
        feedbackRepository.save(newFeedback(travel, UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        assertThat(feedbackRepository.findByTravel_Id(travel.getId())).hasSize(2);
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

    private Feedback newFeedback(Travel travel, UUID travelerId) {
        return Feedback.builder()
                .travel(travel)
                .travelerId(travelerId)
                .rating(5)
                .comment("Great")
                .createdAt(Instant.now(Clock.systemUTC()))
                .build();
    }
}
