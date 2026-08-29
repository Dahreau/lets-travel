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

    // Poids : voyage sans note = 1 (participation neutre), voyage note = (note - 3) (4-5/5 positif,
    // 1-2/5 exclu, 3/5 neutre). Score = somme des poids des voyages similaires.
    @Query("""
            MATCH (me:Traveler {id: $travelerId})-[:PARTICIPATED_IN|RATED]->(liked:Travel)
            WITH DISTINCT me, liked
            OPTIONAL MATCH (me)-[rated:RATED]->(liked)
            WITH me, liked, coalesce(rated.rating - 3, 1) AS weight
            WHERE weight > 0
            MATCH (candidate:Travel)
            WHERE candidate.id <> liked.id
              AND NOT (me)-[:PARTICIPATED_IN]->(candidate)
              AND (candidate.country = liked.country
                   OR candidate.priceRange = liked.priceRange
                   OR candidate.durationRange = liked.durationRange)
            WITH candidate, sum(weight) AS matchScore
            RETURN candidate.id AS travelId
            ORDER BY matchScore DESC
            LIMIT $limit
            """)
    List<String> recommendTravelIds(String travelerId, int limit);
}
