package com.travel_plan.payment_service.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID travelId,
        @NotNull UUID ownerId,
        @NotNull UUID paymentMethodId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency) {
}
