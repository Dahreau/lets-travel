package com.travel_plan.travel_service.web;

// Statistiques personnelles du Traveler connecte (feat/traveler-frontend, docs/lets-travel_project.md).
// cancellationCount compte les abonnements CANCELLED ; participationCount tous statuts confondus
// (voir TravelerStatsService.myStats et SubscriptionRepository pour le detail des requetes).
public record TravelerStatsResponse(
        long participationCount, long feedbackCount, long reportCount, long cancellationCount) {
}
