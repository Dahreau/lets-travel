package com.travel_plan.travel_service.web;

import java.util.List;

// Page publique consultee par un Traveler avant de s'abonner (feat/manager-frontend, voir
// docs/lets-travel_project.md). travelRatings expose le detail voyage par voyage (l'enonce
// demande les "past travel ratings" au pluriel) - averageRating reste null tant qu'aucun
// feedback n'a ete laisse, jamais 0 (qui laisserait croire a une mauvaise note).
public record ManagerPublicStatsResponse(
        long travelCount, Double averageRating, long reportCount, List<ManagerPublicTravelRatingEntry> travelRatings) {
}
