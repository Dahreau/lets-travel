package com.travel_plan.travel_service.graph;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("Place")
@Getter
@Setter
@NoArgsConstructor
public class PlaceNode {

    @Id
    @GeneratedValue
    private Long id;

    private String city;

    private String country;

    @Relationship(type = "ROUTE_TO", direction = Relationship.Direction.OUTGOING)
    private Set<RouteRelationship> routes = new HashSet<>();

    public PlaceNode(String city, String country) {
        this.city = city;
        this.country = country;
    }
}
