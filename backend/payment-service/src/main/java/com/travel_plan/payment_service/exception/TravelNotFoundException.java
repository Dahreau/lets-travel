package com.travel_plan.payment_service.exception;

import java.util.UUID;

// Levee quand travel-service repond 404 pour le travelId demande (voir client.TravelServiceClient).
// Classe locale a payment-service, distincte de travel_service.exception.TravelNotFoundException (pas de code partage entre microservices).
public class TravelNotFoundException extends RuntimeException {

    public TravelNotFoundException(UUID id) {
        super("Voyage introuvable : " + id);
    }
}
