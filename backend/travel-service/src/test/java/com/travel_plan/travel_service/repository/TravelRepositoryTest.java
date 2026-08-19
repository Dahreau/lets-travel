package com.travel_plan.travel_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.travel_service.domain.Accommodation;
import com.travel_plan.travel_service.domain.AccommodationType;
import com.travel_plan.travel_service.domain.Activity;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Transportation;
import com.travel_plan.travel_service.domain.TransportationType;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
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
class TravelRepositoryTest {

    @Autowired
    private TravelRepository travelRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void destinationsAreOrderedByOrderIndex() {
        Travel travel = newTravel();
        Destination paris = destination(travel, "Paris", "France", 1);
        Destination rome = destination(travel, "Rome", "Italy", 0);
        travel.getDestinations().add(paris);
        travel.getDestinations().add(rome);

        travel = travelRepository.save(travel);
        entityManager.flush();
        entityManager.clear();

        Travel reloaded = travelRepository.findById(travel.getId()).orElseThrow();
        assertThat(reloaded.getDestinations()).extracting(Destination::getCity).containsExactly("Rome", "Paris");
    }

    @Test
    void deletingTravelCascadesToDestinationsActivitiesAccommodationAndTransportation() {
        Travel travel = newTravel();
        Destination destination = destination(travel, "Lisbon", "Portugal", 0);
        travel.getDestinations().add(destination);

        Activity activity = Activity.builder()
                .destination(destination)
                .name("Tram 28 tour")
                .date(LocalDate.of(2026, Month.SEPTEMBER, 2))
                .build();
        destination.getActivities().add(activity);

        Accommodation accommodation = Accommodation.builder()
                .destination(destination)
                .name("Alfama Hostel")
                .type(AccommodationType.HOSTEL)
                .address("Rua de Sao Miguel 10")
                .checkIn(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .checkOut(LocalDate.of(2026, Month.SEPTEMBER, 5))
                .build();
        destination.setAccommodation(accommodation);

        Transportation transportation = Transportation.builder()
                .travel(travel)
                .type(TransportationType.FLIGHT)
                .fromLocation("Paris CDG")
                .toLocation("Lisbon LIS")
                .departureTime(Instant.parse("2026-09-01T08:00:00Z"))
                .arrivalTime(Instant.parse("2026-09-01T10:00:00Z"))
                .build();
        travel.getTransportations().add(transportation);

        travel = travelRepository.save(travel);
        entityManager.flush();

        UUID destinationId = destination.getId();
        UUID activityId = activity.getId();
        UUID accommodationId = accommodation.getId();
        UUID transportationId = transportation.getId();

        travelRepository.delete(travel);
        entityManager.flush();

        assertThat(entityManager.find(Destination.class, destinationId)).isNull();
        assertThat(entityManager.find(Activity.class, activityId)).isNull();
        assertThat(entityManager.find(Accommodation.class, accommodationId)).isNull();
        assertThat(entityManager.find(Transportation.class, transportationId)).isNull();
    }

    @Test
    void updatingTravelRefreshesUpdatedAt() {
        Travel travel = travelRepository.save(newTravel());
        entityManager.flush();
        Instant firstUpdatedAt = travel.getUpdatedAt();

        travel.setTitle("Updated title");
        travelRepository.save(travel);
        entityManager.flush();

        assertThat(travel.getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
    }

    private Travel newTravel() {
        return Travel.builder()
                .title("Iberian tour")
                .ownerId(UUID.randomUUID())
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 10))
                .status(TravelStatus.PLANNED)
                .build();
    }

    private Destination destination(Travel travel, String city, String country, int orderIndex) {
        return Destination.builder()
                .travel(travel)
                .city(city)
                .country(country)
                .arrivalDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .departureDate(LocalDate.of(2026, Month.SEPTEMBER, 5))
                .orderIndex(orderIndex)
                .build();
    }
}
