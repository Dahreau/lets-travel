package com.travel_plan.payment_service.web;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentMethodRequest(
        @NotNull UUID ownerId,
        @NotNull ProviderType provider,
        @NotNull MethodType type,
        @NotBlank String providerToken,
        String brand,
        String last4,
        boolean isDefault) {
}
