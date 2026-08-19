package com.travel_plan.travel_service.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.neo4j.test.autoconfigure.DataNeo4jTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataNeo4jTest
@Testcontainers
class PlaceRepositoryTest {

    @Container
    @ServiceConnection
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26");

    @Autowired
    private PlaceRepository placeRepository;

    @Test
    void findsExistingPlaceByCityAndCountry() {
        placeRepository.save(new PlaceNode("Paris", "France"));

        Optional<PlaceNode> found = placeRepository.findByCityAndCountry("Paris", "France");

        assertThat(found).isPresent();
        assertThat(found.get().getCountry()).isEqualTo("France");
    }

    @Test
    void returnsEmptyWhenPlaceDoesNotExist() {
        Optional<PlaceNode> found = placeRepository.findByCityAndCountry("Atlantis", "Nowhere");

        assertThat(found).isEmpty();
    }

    @Test
    void suggestNextDestinationsFollowsRouteToRelationshipUpToTwoHops() {
        PlaceNode paris = placeRepository.save(new PlaceNode("Paris", "France"));
        PlaceNode lyon = placeRepository.save(new PlaceNode("Lyon", "France"));
        PlaceNode rome = placeRepository.save(new PlaceNode("Rome", "Italy"));

        paris.getRoutes().add(new RouteRelationship(lyon, 1));
        paris = placeRepository.save(paris);
        PlaceNode savedLyon = paris.getRoutes().iterator().next().getTo();
        savedLyon.getRoutes().add(new RouteRelationship(rome, 1));
        placeRepository.save(savedLyon);

        List<PlaceNode> suggestions = placeRepository.suggestNextDestinations("Paris", "France");

        assertThat(suggestions).extracting(PlaceNode::getCity).contains("Lyon", "Rome");
    }

    @Test
    void suggestNextDestinationsExcludesTheStartingPlaceItself() {
        PlaceNode paris = placeRepository.save(new PlaceNode("Paris", "France"));
        paris.getRoutes().add(new RouteRelationship(paris, 1));
        placeRepository.save(paris);

        List<PlaceNode> suggestions = placeRepository.suggestNextDestinations("Paris", "France");

        assertThat(suggestions).isEmpty();
    }
}
