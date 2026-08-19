package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.AccommodationType;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.domain.TransportationType;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.graph.TravelGraphSyncService;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.web.AccommodationRequest;
import com.travel_plan.travel_service.web.ActivityRequest;
import com.travel_plan.travel_service.web.DestinationRequest;
import com.travel_plan.travel_service.web.TransportationRequest;
import com.travel_plan.travel_service.web.TravelRequest;
import com.travel_plan.travel_service.web.TravelResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class TravelServiceTest {

    private final TravelRepository travelRepository = mock(TravelRepository.class);
    private final TravelGraphSyncService graphSyncService = mock(TravelGraphSyncService.class);
    private final TravelService travelService = new TravelService(travelRepository, graphSyncService);

    @Test
    void createBuildsFullTravelGraphAndRecordsRoute() {
        when(travelRepository.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TravelResponse saved = travelService.create(fullRequest());

        assertThat(saved.title()).isEqualTo("Iberian tour");
        assertThat(saved.destinations()).hasSize(2);
        assertThat(saved.destinations().get(0).activities()).hasSize(1);
        assertThat(saved.destinations().get(0).accommodation()).isNotNull();
        assertThat(saved.destinations().get(1).accommodation()).isNull();
        assertThat(saved.transportations()).hasSize(1);

        ArgumentCaptor<List<Destination>> captor = ArgumentCaptor.forClass(List.class);
        verify(graphSyncService).recordRoute(captor.capture());
        assertThat(captor.getValue()).extracting(Destination::getCity).containsExactly("Lisbon", "Porto");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(travelRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> travelService.findById(id)).isInstanceOf(TravelNotFoundException.class);
    }

    @Test
    void findByIdReturnsTravelWhenPresent() {
        UUID id = UUID.randomUUID();
        Travel travel = Travel.builder()
                .id(id)
                .title("Iberian tour")
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 8))
                .build();
        when(travelRepository.findById(id)).thenReturn(Optional.of(travel));

        assertThat(travelService.findById(id)).isEqualTo(TravelResponse.from(travel));
    }

    @Test
    void updateReplacesDestinationsAndResyncsRoute() {
        UUID id = UUID.randomUUID();
        Travel existing = Travel.builder()
                .id(id)
                .title("Old title")
                .ownerId(UUID.randomUUID())
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 10))
                .status(TravelStatus.PLANNED)
                .build();
        Destination oldDestination = Destination.builder()
                .travel(existing)
                .city("Madrid")
                .country("Spain")
                .arrivalDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .departureDate(LocalDate.of(2026, Month.SEPTEMBER, 3))
                .orderIndex(0)
                .build();
        existing.getDestinations().add(oldDestination);

        when(travelRepository.findById(id)).thenReturn(Optional.of(existing));
        when(travelRepository.saveAndFlush(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TravelResponse updated = travelService.update(id, fullRequest());

        assertThat(updated.title()).isEqualTo("Iberian tour");
        assertThat(updated.destinations()).hasSize(2);
        verify(graphSyncService).removeRoute(List.of(oldDestination));
    }

    @Test
    void deleteRemovesRouteThenDeletesTravel() {
        UUID id = UUID.randomUUID();
        Travel existing = Travel.builder().id(id).build();
        Destination destination = Destination.builder()
                .travel(existing)
                .city("Lisbon")
                .country("Portugal")
                .orderIndex(0)
                .build();
        existing.getDestinations().add(destination);

        when(travelRepository.findById(id)).thenReturn(Optional.of(existing));

        travelService.delete(id);

        verify(graphSyncService).removeRoute(List.of(destination));
        verify(travelRepository).delete(existing);
    }

    @Test
    void findAllDelegatesToRepository() {
        when(travelRepository.findAll()).thenReturn(List.of(Travel.builder()
                .title("A")
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 8))
                .build()));

        assertThat(travelService.findAll()).hasSize(1);
    }

    private TravelRequest fullRequest() {
        ActivityRequest activity = new ActivityRequest(
                "Tram 28", "City tour", LocalDate.of(2026, Month.SEPTEMBER, 2), new BigDecimal("3.50"));
        AccommodationRequest accommodation = new AccommodationRequest(
                "Alfama Hostel",
                AccommodationType.HOSTEL,
                "Rua de Sao Miguel 10",
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 5));
        DestinationRequest lisbon = new DestinationRequest(
                "Lisbon",
                "Portugal",
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 5),
                0,
                List.of(activity),
                accommodation);
        DestinationRequest porto = new DestinationRequest(
                "Porto",
                "Portugal",
                LocalDate.of(2026, Month.SEPTEMBER, 5),
                LocalDate.of(2026, Month.SEPTEMBER, 8),
                1,
                List.of(),
                null);
        TransportationRequest transportation = new TransportationRequest(
                TransportationType.TRAIN,
                "Lisbon",
                "Porto",
                Instant.parse("2026-09-05T08:00:00Z"),
                Instant.parse("2026-09-05T11:00:00Z"),
                "CP");

        return new TravelRequest(
                "Iberian tour",
                UUID.randomUUID(),
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 8),
                TravelStatus.PLANNED,
                List.of(lisbon, porto),
                List.of(transportation));
    }
}
