package com.travel_plan.travel_service.web;

// Statistiques personnelles du Traveler connecte. cancellationCount = abonnements CANCELLED ;
// participationCount = tous statuts confondus.
public record TravelerStatsResponse(
        long participationCount, long feedbackCount, long reportCount, long cancellationCount) {
}
