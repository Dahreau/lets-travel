package com.travel_plan.payment_service.web;

import com.travel_plan.payment_service.domain.MethodType;
import com.travel_plan.payment_service.domain.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// ownerId est desormais optionnel : obligatoire seulement quand l'appelant est ADMIN (voir
// PaymentMethodService.resolveOwnerId), ignore/ecrase sinon par le userId du JWT appelant.
public record PaymentMethodRequest(
        UUID ownerId,
        @NotNull ProviderType provider,
        @NotNull MethodType type,
        @NotBlank String providerToken,
        String brand,
        String last4,
        boolean isDefault) {
}
