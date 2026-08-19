package com.travel_plan.travel_service.graph;

import com.travel_plan.travel_service.domain.Destination;
import java.util.List;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TravelGraphSyncService {

    private final PlaceRepository placeRepository;
    private final TransactionTemplate neo4jTransactionTemplate;

    // Transaction Neo4j dediee, independante de la transaction JPA de l'appelant.
    public TravelGraphSyncService(PlaceRepository placeRepository, Neo4jTransactionManager neo4jTransactionManager) {
        this.placeRepository = placeRepository;
        this.neo4jTransactionTemplate = new TransactionTemplate(neo4jTransactionManager);
    }

    public void recordRoute(List<Destination> orderedDestinations) {
        neo4jTransactionTemplate.executeWithoutResult(status -> adjustRoutes(orderedDestinations, 1));
    }

    public void removeRoute(List<Destination> orderedDestinations) {
        neo4jTransactionTemplate.executeWithoutResult(status -> adjustRoutes(orderedDestinations, -1));
    }

    private void adjustRoutes(List<Destination> orderedDestinations, int delta) {
        for (int i = 0; i < orderedDestinations.size() - 1; i++) {
            Destination from = orderedDestinations.get(i);
            Destination to = orderedDestinations.get(i + 1);
            adjustRoute(from.getCity(), from.getCountry(), to.getCity(), to.getCountry(), delta);
        }
    }

    // tripCount represente le nombre de voyages utilisant ce trajet, l'arete n'est supprimee que
    // lorsqu'il tombe a 0 (elle peut etre partagee par plusieurs Travel).
    private void adjustRoute(String fromCity, String fromCountry, String toCity, String toCountry, int delta) {
        PlaceNode fromNode = findOrCreate(fromCity, fromCountry);
        PlaceNode toNode = findOrCreate(toCity, toCountry);

        RouteRelationship existing = fromNode.getRoutes().stream()
                .filter(route -> route.getTo().getId().equals(toNode.getId()))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            if (delta > 0) {
                fromNode.getRoutes().add(new RouteRelationship(toNode, delta));
                placeRepository.save(fromNode);
            }
            return;
        }

        int newCount = existing.getTripCount() + delta;
        if (newCount <= 0) {
            fromNode.getRoutes().remove(existing);
        } else {
            existing.setTripCount(newCount);
        }
        placeRepository.save(fromNode);
    }

    private PlaceNode findOrCreate(String city, String country) {
        return placeRepository
                .findByCityAndCountry(city, country)
                .orElseGet(() -> placeRepository.save(new PlaceNode(city, country)));
    }
}
