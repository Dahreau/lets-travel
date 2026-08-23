package com.travel_plan.payment_service.client;

import java.math.BigDecimal;
import java.util.UUID;

// Sous-ensemble de travel-service.web.TravelResponse : sert aussi de forme de
// deserialisation directe de la reponse JSON de GET /api/travels/{id} - Jackson
// ignore les champs en trop (title, dates, destinations, ...) par defaut, seuls
// id/price/currency nous interessent pour facturer.
public record TravelSummary(UUID id, BigDecimal price, String currency) {
}
