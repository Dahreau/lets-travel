package com.travel_plan.payment_service.exception;

import java.util.UUID;

public class InvalidRefundException extends RuntimeException {

    public InvalidRefundException(UUID id) {
        super("Le paiement " + id + " ne peut pas etre rembourse dans son statut actuel");
    }
}
