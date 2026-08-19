package com.travel_plan.payment_service.web;

import com.travel_plan.payment_service.domain.Payment;
import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID travelId,
        UUID ownerId,
        UUID paymentMethodId,
        BigDecimal amount,
        String currency,
        ProviderType provider,
        PaymentStatus status,
        String providerReference,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        UUID paymentMethodId = payment.getPaymentMethod() == null ? null : payment.getPaymentMethod().getId();
        return new PaymentResponse(
                payment.getId(),
                payment.getTravelId(),
                payment.getOwnerId(),
                paymentMethodId,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getProviderReference(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
