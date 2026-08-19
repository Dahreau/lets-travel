package com.travel_plan.payment_service.exception;

import java.util.UUID;

public class PaymentMethodNotFoundException extends RuntimeException {

    public PaymentMethodNotFoundException(UUID id) {
        super("Payment method not found: " + id);
    }
}
