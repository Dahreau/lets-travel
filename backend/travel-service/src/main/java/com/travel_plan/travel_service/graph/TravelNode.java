package com.travel_plan.travel_service.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

// Noeud distinct de PlaceNode (graphe Place/Route, feature "suggestions de prochaine
// destination" heritee de travel-plan) : celui-ci alimente les recommandations
// personnalisees Let's Travel (feat/search-and-recommendations), basees sur ce qu'un
// Traveler a deja suivi/note - pas sur les trajets entre villes. Deux graphes distincts,
// deux finalites, dans la meme instance Neo4j - voir RecommendationRepository.
@Node("Travel")
@Getter
@Setter
@NoArgsConstructor
public class TravelNode {

    @Id
    private String id;

    // Les 3 champs du voyage utilises pour la similarite (enonce Let's Travel : "use at
    // least 3 fields of the travel") - voir RecommendationSyncService pour le calcul de ces
    // tranches a partir du Travel reel (Postgres), et RecommendationRepository.recommendTravelIds
    // pour la requete qui les compare.
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
