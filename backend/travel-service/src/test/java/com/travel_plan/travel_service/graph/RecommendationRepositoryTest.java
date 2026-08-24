package com.travel_plan.travel_service.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.neo4j.test.autoconfigure.DataNeo4jTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataNeo4jTest
@Testcontainers
class RecommendationRepositoryTest {

    @Container
    @ServiceConnection
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26");

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Test
    void upsertTravelCreatesThenUpdatesSameNode() {
        recommendationRepository.upsertTravel("t1", "Portugal", "BUDGET", "SHORT");
        recommendationRepository.upsertTravel("t1", "Portugal", "STANDARD", "SHORT");

        TravelNode node = recommendationRepository.findById("t1").orElseThrow();
        assertThat(node.getPriceRange()).isEqualTo("STANDARD");
    }

    @Test
    void deleteTravelRemovesTheNode() {
        recommendationRepository.upsertTravel("t1", "Portugal", "BUDGET", "SHORT");

        recommendationRepository.deleteTravel("t1");

        assertThat(recommendationRepository.findById("t1")).isEmpty();
    }

    @Test
    void removeParticipationDeletesOnlyTheRelationship() {
        recommendationRepository.upsertTravel("t1", "Portugal", "BUDGET", "SHORT");
        recommendationRepository.recordParticipation("traveler1", "t1");

        recommendationRepository.removeParticipation("traveler1", "t1");

        // Le noeud Travel doit survivre : seule la relation PARTICIPATED_IN est supprimee.
        assertThat(recommendationRepository.findById("t1")).isPresent();
        assertThat(recommendationRepository.recommendTravelIds("traveler1", 10)).isEmpty();
    }

    // Recommandation basee sur le contenu : "traveler1" a participe a t1 (Portugal/BUDGET/SHORT).
    // t2 partage le pays -> recommande. t3 ne partage aucun des 3 champs -> pas recommande.
    // t1 lui-meme est exclu (deja participe).
    @Test
    void recommendTravelIdsExcludesParticipatedAndUnrelatedTravelsButIncludesSimilarOnes() {
        recommendationRepository.upsertTravel("t1", "Portugal", "BUDGET", "SHORT");
        recommendationRepository.upsertTravel("t2", "Portugal", "PREMIUM", "LONG");
        recommendationRepository.upsertTravel("t3", "Japan", "PREMIUM", "LONG");
        recommendationRepository.recordParticipation("traveler1", "t1");

        List<String> recommended = recommendationRepository.recommendTravelIds("traveler1", 10);

        assertThat(recommended).containsExactly("t2");
    }

    @Test
    void recommendTravelIdsRanksHigherMatchScoreFirst() {
        // liked1 et liked2 volontairement sans aucun champ en commun entre eux, pour que le
        // matchScore d'un candidat reflete precisement avec COMBIEN des deux il partage un champ.
        recommendationRepository.upsertTravel("liked1", "Portugal", "BUDGET", "SHORT");
        recommendationRepository.upsertTravel("liked2", "Japan", "PREMIUM", "LONG");
        // "twoMatches" partage un champ different avec chacun des deux voyages aimes -> matchScore 2.
        recommendationRepository.upsertTravel("twoMatches", "Portugal", "PREMIUM", "MEDIUM");
        // "oneMatch" ne partage un champ qu'avec liked1 (le pays) -> matchScore 1.
        recommendationRepository.upsertTravel("oneMatch", "Portugal", "STANDARD", "MEDIUM");
        recommendationRepository.recordParticipation("traveler1", "liked1");
        recommendationRepository.recordFeedback("traveler1", "liked2", 5);

        List<String> recommended = recommendationRepository.recommendTravelIds("traveler1", 10);

        assertThat(recommended).containsExactly("twoMatches", "oneMatch");
    }

    @Test
    void recommendTravelIdsReturnsEmptyWhenTravelerHasNoHistory() {
        recommendationRepository.upsertTravel("t1", "Portugal", "BUDGET", "SHORT");

        assertThat(recommendationRepository.recommendTravelIds("newTraveler", 10)).isEmpty();
    }
}
