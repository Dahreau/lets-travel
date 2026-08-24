package com.travel_plan.travel_service.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.TransactionStatus;

class RecommendationSyncServiceTest {

    private final RecommendationRepository recommendationRepository = mock(RecommendationRepository.class);
    private final Neo4jTransactionManager neo4jTransactionManager = mock(Neo4jTransactionManager.class);
    private final RecommendationSyncService service =
            new RecommendationSyncService(recommendationRepository, neo4jTransactionManager);

    @BeforeEach
    void stubTransactionManager() {
        when(neo4jTransactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void upsertTravelDerivesCountryPriceRangeAndDurationRangeFromTheFirstDestination() {
        UUID travelId = UUID.randomUUID();
        Travel travel = Travel.builder()
                .id(travelId)
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 5))
                .price(new BigDecimal("300"))
                .build();
        Destination lisbon = Destination.builder().travel(travel).city("Lisbon").country("Portugal").orderIndex(0)
                .build();
        Destination porto = Destination.builder().travel(travel).city("Porto").country("Portugal").orderIndex(1)
                .build();
        travel.getDestinations().add(porto);
        travel.getDestinations().add(lisbon);

        service.upsertTravel(travel);

        // duree = 5 jours inclus -> SHORT_MAX_DAYS(3) depasse, MEDIUM_MAX_DAYS(7) respecte -> MEDIUM
        // prix 300 < BUDGET_MAX(500) -> BUDGET ; premiere destination par orderIndex = Lisbon
        verify(recommendationRepository).upsertTravel(travelId.toString(), "Portugal", "BUDGET", "MEDIUM");
    }

    @Test
    void upsertTravelUsesNullPriceRangeWhenPriceNotSet() {
        UUID travelId = UUID.randomUUID();
        Travel travel = Travel.builder()
                .id(travelId)
                .startDate(LocalDate.of(2026, Month.SEPTEMBER, 1))
                .endDate(LocalDate.of(2026, Month.SEPTEMBER, 2))
                .price(null)
                .build();
        Destination lisbon = Destination.builder().travel(travel).city("Lisbon").country("Portugal").orderIndex(0)
                .build();
        travel.getDestinations().add(lisbon);

        service.upsertTravel(travel);

        verify(recommendationRepository).upsertTravel(travelId.toString(), "Portugal", null, "SHORT");
    }

    @Test
    void deleteTravelDelegatesToRepository() {
        UUID travelId = UUID.randomUUID();

        service.deleteTravel(travelId);

        verify(recommendationRepository).deleteTravel(travelId.toString());
    }

    @Test
    void recordParticipationDelegatesToRepository() {
        UUID travelerId = UUID.randomUUID();
        UUID travelId = UUID.randomUUID();

        service.recordParticipation(travelerId, travelId);

        verify(recommendationRepository).recordParticipation(travelerId.toString(), travelId.toString());
    }

    @Test
    void removeParticipationDelegatesToRepository() {
        UUID travelerId = UUID.randomUUID();
        UUID travelId = UUID.randomUUID();

        service.removeParticipation(travelerId, travelId);

        verify(recommendationRepository).removeParticipation(travelerId.toString(), travelId.toString());
    }

    @Test
    void recordFeedbackDelegatesToRepository() {
        UUID travelerId = UUID.randomUUID();
        UUID travelId = UUID.randomUUID();

        service.recordFeedback(travelerId, travelId, 5);

        verify(recommendationRepository).recordFeedback(travelerId.toString(), travelId.toString(), 5);
    }

    @Test
    void recommendConvertsReturnedIdsToUuids() {
        UUID travelerId = UUID.randomUUID();
        UUID recommendedId = UUID.randomUUID();
        when(recommendationRepository.recommendTravelIds(travelerId.toString(), 10))
                .thenReturn(List.of(recommendedId.toString()));

        List<UUID> results = service.recommend(travelerId);

        assertThat(results).containsExactly(recommendedId);
    }
}
