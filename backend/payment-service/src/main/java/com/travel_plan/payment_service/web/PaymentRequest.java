package com.travel_plan.payment_service.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// amount/currency retires : le montant vient desormais de travel-service, plus jamais du client.
// ownerId optionnel : obligatoire seulement pour un appelant ADMIN (voir PaymentService.resolveOwnerId).
public record PaymentRequest(@NotNull UUID travelId, UUID ownerId, @NotNull UUID paymentMethodId) {
}
