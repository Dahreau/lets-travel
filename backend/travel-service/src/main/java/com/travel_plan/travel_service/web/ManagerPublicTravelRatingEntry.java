package com.travel_plan.travel_service.web;

import java.util.UUID;

// Une ligne du detail voyage par voyage affiche sur la page publique du manager (voir
// ManagerPublicStatsResponse.travelRatings) - averageRating null tant qu'aucun avis n'existe.
public record ManagerPublicTravelRatingEntry(UUID travelId, String title, Double averageRating, long feedbackCount) {
}
