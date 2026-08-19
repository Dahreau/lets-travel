package com.travel_plan.travel_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
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
class SubscriptionRepositoryTest {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private TravelRepository travelRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsActiveSubscriptionForTravelAndTraveler() {
        Travel travel = travelRepository.save(newTravel());
        UUID travelerId = UUID.randomUUID();
        subscriptionRepository.save(newSubscription(travel, travelerId, SubscriptionStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();

        assertThat(subscriptionRepository.findByTravel_IdAndTravelerIdAndStatus(
                        travel.getId(), travelerId, SubscriptionStatus.ACTIVE))
                .isPresent();
    }

    @Test
    void doesNotFindCancelledSubscriptionAsActive() {
        Travel travel = travelRepository.save(newTravel());
        UUID travelerId = UUID.randomUUID();
        subscriptionRepository.save(newSubscription(travel, travelerId, SubscriptionStatus.CANCELLED));
        entityManager.flush();
        entityManager.clear();

        assertThat(subscriptionRepository.findByTravel_IdAndTravelerIdAndStatus(
                        travel.getId(), travelerId, SubscriptionStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void listsAllSubscribersForATravel() {
        Travel travel = travelRepository.save(newTravel());
        subscriptionRepository.save(newSubscription(travel, UUID.randomUUID(), SubscriptionStatus.ACTIVE));
        subscriptionRepository.save(newSubscription(travel, UUID.randomUUID(), SubscriptionStatus.CANCELLED));
        entityManager.flush();
        entityManager.clear();

        assertThat(subscriptionRepository.findByTravel_Id(travel.getId())).hasSize(2);
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

    private Subscription newSubscription(Travel travel, UUID travelerId, SubscriptionStatus status) {
        return Subscription.builder()
                .travel(travel)
                .travelerId(travelerId)
                .status(status)
                .subscribedAt(Instant.now(Clock.systemUTC()))
                .build();
    }
}
