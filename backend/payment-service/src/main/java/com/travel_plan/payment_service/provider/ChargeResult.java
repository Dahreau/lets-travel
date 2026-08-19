package com.travel_plan.payment_service.provider;

import com.travel_plan.payment_service.domain.PaymentStatus;

public record ChargeResult(String providerReference, PaymentStatus status) {
}
