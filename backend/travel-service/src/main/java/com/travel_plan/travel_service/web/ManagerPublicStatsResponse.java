package com.travel_plan.travel_service.web;

// Page publique consultee par un Traveler avant de s'abonner (feat/manager-frontend, voir
// docs/lets-travel_project.md). Uniquement des compteurs/moyennes agreges - averageRating est
// null tant qu'aucun feedback n'a ete laisse, jamais 0 (qui laisserait croire a une mauvaise note).
public record ManagerPublicStatsResponse(long travelCount, Double averageRating, long reportCount) {
}
