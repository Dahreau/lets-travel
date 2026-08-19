package com.travel_plan.travel_service.exception;

// Levee quand l'appelant n'a pas de userId lie (compte ADMIN par defaut, sans fiche
// User) - il ne peut pas s'abonner en tant que traveler, il n'y a pas de traveler.
public class InvalidSubscriptionRequestException extends RuntimeException {

    public InvalidSubscriptionRequestException(String message) {
        super(message);
    }
}
