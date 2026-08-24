package com.travel_plan.travel_service.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

// Noeud distinct de PlaceNode (graphe Place/Route) : alimente les recommandations basees sur
// l'historique du Traveler, pas les trajets entre villes (voir RecommendationRepository).
@Node("Travel")
@Getter
@Setter
@NoArgsConstructor
public class TravelNode {

    @Id
    private String id;

    // Les 3 champs utilises pour la similarite (calcul dans RecommendationSyncService).
    private String country;
    private String priceRange;
    private String durationRange;

    public TravelNode(String id, String country, String priceRange, String durationRange) {
        this.id = id;
        this.country = country;
        this.priceRange = priceRange;
        this.durationRange = durationRange;
    }
}
