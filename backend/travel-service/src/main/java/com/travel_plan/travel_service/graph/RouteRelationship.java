package com.travel_plan.travel_service.graph;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class RouteRelationship {

    @RelationshipId
    @GeneratedValue
    private Long id;

    @TargetNode
    private PlaceNode to;

    private int tripCount;

    public RouteRelationship(PlaceNode to, int tripCount) {
        this.to = to;
        this.tripCount = tripCount;
    }
}
