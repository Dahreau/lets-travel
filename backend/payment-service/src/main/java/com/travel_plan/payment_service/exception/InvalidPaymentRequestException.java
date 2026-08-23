package com.travel_plan.payment_service.exception;

// Levee quand l'appelant ADMIN ne fournit pas ownerId (obligatoire pour lui, puisque son
// propre JWT n'en porte pas) - mirroir de InvalidTravelRequestException cote travel-service.
public class InvalidPaymentRequestException extends RuntimeException {

    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
