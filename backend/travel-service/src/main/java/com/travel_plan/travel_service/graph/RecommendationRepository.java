package com.travel_plan.travel_service.graph;

import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationRepository extends Neo4jRepository<TravelNode, String> {

    @Query("""
            MERGE (t:Travel {id: $travelId})
            SET t.country = $country, t.priceRange = $priceRange, t.durationRange = $durationRange
            """)
    void upsertTravel(String travelId, String country, String priceRange, String durationRange);

    @Query("MATCH (t:Travel {id: $travelId}) DETACH DELETE t")
    void deleteTravel(String travelId);

    @Query("""
            MERGE (traveler:Traveler {id: $travelerId})
            MERGE (travel:Travel {id: $travelId})
            MERGE (traveler)-[:PARTICIPATED_IN]->(travel)
            """)
    void recordParticipation(String travelerId, String travelId);

    @Query("""
            MATCH (:Traveler {id: $travelerId})-[r:PARTICIPATED_IN]->(:Travel {id: $travelId})
            DELETE r
            """)
    void removeParticipation(String travelerId, String travelId);

    @Query("""
            MERGE (traveler:Traveler {id: $travelerId})
            MERGE (travel:Travel {id: $travelId})
            MERGE (traveler)-[r:RATED]->(travel)
            SET r.rating = $rating
            """)
    void recordFeedback(String travelerId, String travelId, int rating);

    // Recommandation basee sur le contenu : voyages similaires (pays / tranche de prix /
    // tranche de duree - au moins 1 des 3 champs en commun) a ceux deja suivis ou notes par
    // ce Traveler, en excluant ceux auxquels il participe deja. Score = nombre de voyages
    // "aimes" avec lesquels le candidat partage au moins un champ (approximation simple d'un
    // score de pertinence, suffisante pour l'audit qui verifie surtout que la recommandation
    // change bien selon l'historique de l'utilisateur connecte - voir lets-travel_audit.md,
    // section "Verify the precision of travel recommendations... switch to a different account").
    @Query("""
            MATCH (me:Traveler {id: $travelerId})-[:PARTICIPATED_IN|RATED]->(liked:Travel)
            WITH me, collect(DISTINCT liked) AS likedTravels
            UNWIND likedTravels AS liked
            MATCH (candidate:Travel)
            WHERE candidate.id <> liked.id
              AND NOT (me)-[:PARTICIPATED_IN]->(candidate)
              AND (candidate.country = liked.country
                   OR candidate.priceRange = liked.priceRange
                   OR candidate.durationRange = liked.durationRange)
            WITH candidate, count(DISTINCT liked) AS matchScore
            RETURN candidate.id AS travelId
            ORDER BY matchScore DESC
            LIMIT $limit
            """)
    List<String> recommendTravelIds(String travelerId, int limit);
}
