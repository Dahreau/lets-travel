package com.travel_plan.travel_service.exception;

import java.util.UUID;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(UUID id) {
        super("Abonnement introuvable : " + id);
    }
}
