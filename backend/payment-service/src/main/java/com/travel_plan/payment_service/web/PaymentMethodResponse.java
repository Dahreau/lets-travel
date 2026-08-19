package com.travel_plan.payment_service.web;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.PaymentMethod;
import com.travel_plan.payment_service.domain.ProviderType;
import java.time.Instant;
import java.util.UUID;

public record PaymentMethodResponse(
        UUID id,
        UUID ownerId,
        ProviderType provider,
        MethodType type,
        String brand,
        String last4,
        boolean isDefault,
        Instant createdAt) {

    public static PaymentMethodResponse from(PaymentMethod method) {
        return new PaymentMethodResponse(
                method.getId(),
                method.getOwnerId(),
                method.getProvider(),
                method.getType(),
                method.getBrand(),
                method.getLast4(),
                method.isDefault(),
                method.getCreatedAt());
    }
}
