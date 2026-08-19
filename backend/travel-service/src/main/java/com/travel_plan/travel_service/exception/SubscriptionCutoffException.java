package com.travel_plan.travel_service.exception;

// Levee quand un desabonnement est tente a moins de 3 jours du depart du travel
// (regle explicitement demandee par l'enonce Let's Travel).
public class SubscriptionCutoffException extends RuntimeException {

    public SubscriptionCutoffException(String message) {
        super(message);
    }
}
