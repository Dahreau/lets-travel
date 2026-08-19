package com.travel_plan.payment_service.exception;

import java.util.UUID;

public class InvalidRefundException extends RuntimeException {

    public InvalidRefundException(UUID id) {
        super("Payment " + id + " cannot be refunded from its current status");
    }
}
