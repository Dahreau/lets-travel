package com.travel_plan.travel_service.search;

// Non verifiee : remonte a travers TravelService (meme transaction JPA que l'appelant, voir
// TravelSearchService) jusqu'a ApiExceptionHandler.handleUnexpected (catch-all -> 500). Pas de
// handler dedie : une panne Elasticsearch est une panne infra imprevue, pas une erreur metier.
public class TravelSearchException extends RuntimeException {

    public TravelSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
