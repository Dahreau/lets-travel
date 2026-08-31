package com.travel_plan.payment_service.exception;

// Leve quand le circuit breaker de TravelServiceClient est ouvert (panne prolongee) ou que
// les tentatives d'appel a travel-service ont toutes echoue - mappee en 503 par ApiExceptionHandler.
public class TravelServiceUnavailableException extends RuntimeException {

    public TravelServiceUnavailableException() {
        super("Le service voyages est temporairement indisponible, reessayez dans quelques instants");
    }

    public TravelServiceUnavailableException(Throwable cause) {
        super("Le service voyages est temporairement indisponible, reessayez dans quelques instants", cause);
    }
}
