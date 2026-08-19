package com.travel_plan.travel_service.graph;

import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceRepository extends Neo4jRepository<PlaceNode, Long> {

    Optional<PlaceNode> findByCityAndCountry(String city, String country);

    @Query("""
            MATCH (start:Place {city: $city, country: $country})-[:ROUTE_TO*1..2]->(suggestion:Place)
            WHERE suggestion.city <> $city OR suggestion.country <> $country
            RETURN DISTINCT suggestion
            """)
    List<PlaceNode> suggestNextDestinations(String city, String country);
}
