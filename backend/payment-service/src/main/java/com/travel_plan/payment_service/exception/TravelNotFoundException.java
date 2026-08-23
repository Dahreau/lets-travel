package com.travel_plan.payment_service.exception;

import java.util.UUID;

// Levee quand travel-service repond 404 pour le travelId demande (voir
// client.TravelServiceClient). Distincte de travel_service.exception.TravelNotFoundException :
// c'est une classe locale a payment-service, pas un import cross-service (chaque
// microservice reste independant, pas de code partage entre les deux).
public class TravelNotFoundException extends RuntimeException {

    public TravelNotFoundException(UUID id) {
        super("Travel not found: " + id);
    }
}
