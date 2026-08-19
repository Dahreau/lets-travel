package com.travel_plan.travel_service.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Destination;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.TransactionStatus;

class TravelGraphSyncServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final Neo4jTransactionManager neo4jTransactionManager = mock(Neo4jTransactionManager.class);
    private final TravelGraphSyncService service = new TravelGraphSyncService(placeRepository, neo4jTransactionManager);

    @BeforeEach
    void stubTransactionManager() {
        when(neo4jTransactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void recordRouteCreatesPlacesAndRouteRelationshipForNewDestinations() {
        Destination paris = destination("Paris", "France");
        Destination rome = destination("Rome", "Italy");

        when(placeRepository.findByCityAndCountry("Paris", "France")).thenReturn(Optional.empty());
        when(placeRepository.findByCityAndCountry("Rome", "Italy")).thenReturn(Optional.empty());
        when(placeRepository.save(any(PlaceNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordRoute(List.of(paris, rome));

        verify(placeRepository, org.mockito.Mockito.atLeastOnce()).save(any(PlaceNode.class));
    }

    @Test
    void recordRouteIncrementsTripCountWhenRouteAlreadyExists() {
        Destination paris = destination("Paris", "France");
        Destination rome = destination("Rome", "Italy");

        PlaceNode parisNode = new PlaceNode("Paris", "France");
        parisNode.setId(1L);
        PlaceNode romeNode = new PlaceNode("Rome", "Italy");
        romeNode.setId(2L);
        parisNode.getRoutes().add(new RouteRelationship(romeNode, 1));

        when(placeRepository.findByCityAndCountry("Paris", "France")).thenReturn(Optional.of(parisNode));
        when(placeRepository.findByCityAndCountry("Rome", "Italy")).thenReturn(Optional.of(romeNode));
        when(placeRepository.save(any(PlaceNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordRoute(List.of(paris, rome));

        assertThat(parisNode.getRoutes()).extracting(RouteRelationship::getTripCount).containsExactly(2);
    }

    @Test
    void removeRouteDeletesRelationshipWhenTripCountReachesZero() {
        Destination paris = destination("Paris", "France");
        Destination rome = destination("Rome", "Italy");

        PlaceNode parisNode = new PlaceNode("Paris", "France");
        parisNode.setId(1L);
        PlaceNode romeNode = new PlaceNode("Rome", "Italy");
        romeNode.setId(2L);
        parisNode.getRoutes().add(new RouteRelationship(romeNode, 1));

        when(placeRepository.findByCityAndCountry("Paris", "France")).thenReturn(Optional.of(parisNode));
        when(placeRepository.findByCityAndCountry("Rome", "Italy")).thenReturn(Optional.of(romeNode));
        when(placeRepository.save(any(PlaceNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.removeRoute(List.of(paris, rome));

        assertThat(parisNode.getRoutes()).isEmpty();
    }

    @Test
    void singleDestinationProducesNoRoute() {
        Destination paris = destination("Paris", "France");

        service.recordRoute(List.of(paris));

        verify(placeRepository, never()).save(any());
    }

    private Destination destination(String city, String country) {
        return Destination.builder().city(city).country(country).build();
    }
}
