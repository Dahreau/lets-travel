package com.travel_plan.travel_service.exception;

// Levee quand un traveler tente de s'abonner a un travel auquel il a deja un
// abonnement ACTIVE - pas de doublon d'abonnement actif.
public class DuplicateSubscriptionException extends RuntimeException {

    public DuplicateSubscriptionException(String message) {
        super(message);
    }
}
