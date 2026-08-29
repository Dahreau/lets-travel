package com.travel_plan.payment_service.client;

import java.math.BigDecimal;
import java.util.UUID;

// Sous-ensemble de travel-service.web.TravelResponse, deserialise directement la reponse JSON
// de GET /api/travels/{id} (Jackson ignore les champs en trop) ; seuls price/currency servent a facturer.
public record TravelSummary(UUID id, BigDecimal price, String currency) {
}
