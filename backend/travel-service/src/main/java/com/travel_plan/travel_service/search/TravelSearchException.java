package com.travel_plan.travel_service.search;

// Remonte au catch-all ApiExceptionHandler (500) : panne infra imprevue, pas une erreur metier.
public class TravelSearchException extends RuntimeException {

    public TravelSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
