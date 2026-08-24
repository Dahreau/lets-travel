package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.AccommodationType;
import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.domain.TransportationType;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidTravelRequestException;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.graph.RecommendationSyncService;
import com.travel_plan.travel_service.graph.TravelGraphSyncService;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.search.TravelSearchService;
import com.travel_plan.travel_service.security.AuthenticatedUser;
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
    private final TravelSearchService searchService = mock(TravelSearchService.class);
    private final RecommendationSyncService recommendationSyncService = mock(RecommendationSyncService.class);
    private final TravelService travelService =
            new TravelService(travelRepository, graphSyncService, searchService, recommendationSyncService);

    private final AuthenticatedUser admin = new AuthenticatedUser("admin", "ADMIN", null);

    @Test
    void createBuildsFullTravelGraphAndRecordsRoute() {
        when(travelRepository.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TravelResponse saved = travelService.create(fullRequest(UUID.randomUUID()), admin);

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

    // feat/search-and-recommendations : create() doit aussi indexer le voyage sur Elasticsearch
    // et l'inserer dans le graphe de recommandations - verifie separement de l'assertion metier
    // ci-dessus pour rester lisible.
    @Test
    void createIndexesTravelForSearchAndRecommendations() {
        when(travelRepository.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        travelService.create(fullRequest(UUID.randomUUID()), admin);

        verify(searchService).index(any(Travel.class));
        verify(recommendationSyncService).upsertTravel(any(Travel.class));
    }

    @Test
    void createAsAdminWithoutManagerIdIsRejected() {
        TravelRequest request = fullRequest(null);

        assertThatThrownBy(() -> travelService.create(request, admin))
                .isInstanceOf(InvalidTravelRequestException.class);
    }

    @Test
    void createAsManagerForcesManagerIdFromJwtIgnoringRequestValue() {
        UUID managerUserId = UUID.randomUUID();
        AuthenticatedUser manager = new AuthenticatedUser("manager1", "TRAVEL_MANAGER", managerUserId);
        when(travelRepository.save(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Le manager (ou un client malveillant) tente de s'attribuer un autre managerId : ignore.
        TravelResponse saved = travelService.create(fullRequest(UUID.randomUUID()), manager);

        assertThat(saved.managerId()).isEqualTo(managerUserId);
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
                .managerId(UUID.randomUUID())
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

        TravelResponse updated = travelService.update(id, fullRequest(UUID.randomUUID()), admin);

        assertThat(updated.title()).isEqualTo("Iberian tour");
        assertThat(updated.destinations()).hasSize(2);
        verify(graphSyncService).removeRoute(List.of(oldDestination));
        verify(searchService).index(any(Travel.class));
        verify(recommendationSyncService).upsertTravel(any(Travel.class));
    }

    @Test
    void updateByNonOwningManagerIsForbidden() {
        UUID id = UUID.randomUUID();
        Travel existing = Travel.builder()
                .id(id)
                .title("Old title")
                .managerId(UUID.randomUUID())
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 10))
                .status(TravelStatus.PLANNED)
                .build();
        when(travelRepository.findById(id)).thenReturn(Optional.of(existing));

        AuthenticatedUser someOtherManager = new AuthenticatedUser("manager2", "TRAVEL_MANAGER", UUID.randomUUID());
        TravelRequest request = fullRequest(UUID.randomUUID());

        assertThatThrownBy(() -> travelService.update(id, request, someOtherManager))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateByOwningManagerSucceeds() {
        UUID id = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        Travel existing = Travel.builder()
                .id(id)
                .title("Old title")
                .managerId(managerUserId)
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 10))
                .status(TravelStatus.PLANNED)
                .build();
        when(travelRepository.findById(id)).thenReturn(Optional.of(existing));
        when(travelRepository.saveAndFlush(any(Travel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthenticatedUser owningManager = new AuthenticatedUser("manager1", "TRAVEL_MANAGER", managerUserId);

        TravelResponse updated = travelService.update(id, fullRequest(UUID.randomUUID()), owningManager);

        assertThat(updated.managerId()).isEqualTo(managerUserId);
    }

    @Test
    void deleteRemovesRouteThenDeletesTravel() {
        UUID id = UUID.randomUUID();
        Travel existing = Travel.builder().id(id).managerId(UUID.randomUUID()).build();
        Destination destination = Destination.builder()
                .travel(existing)
                .city("Lisbon")
                .country("Portugal")
                .orderIndex(0)
                .build();
        existing.getDestinations().add(destination);

        when(travelRepository.findById(id)).thenReturn(Optional.of(existing));

        travelService.delete(id, admin);

        verify(graphSyncService).removeRoute(List.of(destination));
        verify(searchService).delete(id);
        verify(recommendationSyncService).deleteTravel(id);
        verify(travelRepository).delete(existing);
    }

    @Test
    void deleteByNonOwningManagerIsForbidden() {
        UUID id = UUID.randomUUID();
        Travel existing = Travel.builder().id(id).managerId(UUID.randomUUID()).build();
        when(travelRepository.findById(id)).thenReturn(Optional.of(existing));

        AuthenticatedUser someOtherManager = new AuthenticatedUser("manager2", "TRAVEL_MANAGER", UUID.randomUUID());

        assertThatThrownBy(() -> travelService.delete(id, someOtherManager)).isInstanceOf(ForbiddenException.class);
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

    // feat/search-and-recommendations : search()/autocomplete() resolvent les ids Elasticsearch
    // contre Postgres en preservant l'ordre de pertinence renvoye par l'index (pas l'ordre
    // findAllById, qui n'est pas garanti).
    @Test
    void searchResolvesIdsAgainstPostgresPreservingRelevanceOrder() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Travel first = minimalTravel(firstId, "A");
        Travel second = minimalTravel(secondId, "B");
        when(searchService.search("lisbon")).thenReturn(List.of(secondId, firstId));
        // findAllById renvoie dans un ordre non garanti (ici volontairement "inverse" de la
        // pertinence) pour prouver que le service reordonne bien selon searchService.
        when(travelRepository.findAllById(List.of(secondId, firstId))).thenReturn(List.of(first, second));

        List<TravelResponse> results = travelService.search("lisbon");

        assertThat(results).extracting(TravelResponse::id).containsExactly(secondId, firstId);
    }

    @Test
    void searchReturnsEmptyWhenNoHits() {
        when(searchService.search("nowhere")).thenReturn(List.of());

        assertThat(travelService.search("nowhere")).isEmpty();
        verify(travelRepository, never()).findAllById(any());
    }

    @Test
    void autocompleteDelegatesToSearchServiceAutocomplete() {
        UUID id = UUID.randomUUID();
        Travel travel = minimalTravel(id, "Lisbon tour");
        when(searchService.autocomplete("lis")).thenReturn(List.of(id));
        when(travelRepository.findAllById(List.of(id))).thenReturn(List.of(travel));

        List<TravelResponse> results = travelService.autocomplete("lis");

        assertThat(results).extracting(TravelResponse::id).containsExactly(id);
    }

    // feat/search-and-recommendations : recommendations() est toujours "pour l'utilisateur
    // connecte" - un ADMIN sans userId lie recoit une liste vide plutot qu'une erreur.
    @Test
    void recommendationsReturnsEmptyForCallerWithoutLinkedUserId() {
        List<TravelResponse> results = travelService.recommendations(admin);

        assertThat(results).isEmpty();
        verify(recommendationSyncService, never()).recommend(any());
    }

    @Test
    void recommendationsResolvesRecommendedIdsPreservingOrder() {
        UUID travelerId = UUID.randomUUID();
        AuthenticatedUser traveler = new AuthenticatedUser("traveler1", "TRAVELER", travelerId);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Travel first = minimalTravel(firstId, "A");
        Travel second = minimalTravel(secondId, "B");
        when(recommendationSyncService.recommend(travelerId)).thenReturn(List.of(firstId, secondId));
        when(travelRepository.findAllById(List.of(firstId, secondId))).thenReturn(List.of(second, first));

        List<TravelResponse> results = travelService.recommendations(traveler);

        assertThat(results).extracting(TravelResponse::id).containsExactly(firstId, secondId);
    }

    // Travel minimal pour les tests search/autocomplete/recommendations : startDate/endDate
    // doivent toujours etre renseignes des qu'un Travel passe par TravelResponse.from(...),
    // qui calcule la duree du voyage (Travel.getDurationDays(), ChronoUnit.DAYS.between(...))
    // - sans les deux dates, NullPointerException ("temporal1Inclusive" null). Meme exigence que
    // le Travel construit par findAllDelegatesToRepository plus haut dans ce fichier.
    private Travel minimalTravel(UUID id, String title) {
        return Travel.builder()
                .id(id)
                .title(title)
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 8))
                .build();
    }

    private TravelRequest fullRequest(UUID managerId) {
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
                managerId,
                LocalDate.of(2026, Month.SEPTEMBER, 1),
                LocalDate.of(2026, Month.SEPTEMBER, 8),
                TravelStatus.PLANNED,
                new BigDecimal("450.00"),
                "EUR",
                List.of(lisbon, porto),
                List.of(transportation));
    }
}
