package com.travel_plan.payment_service.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// amount/currency ont ete retires : le montant vient desormais de travel-service (voir
// client/TravelServiceClient), plus jamais du client (voir docs/nouveautes-vs-travel-plan.md).
// ownerId reste optionnel ici : obligatoire seulement quand l'appelant est ADMIN (voir
// PaymentService.resolveOwnerId), ignore/ecrase sinon.
public record PaymentRequest(@NotNull UUID travelId, UUID ownerId, @NotNull UUID paymentMethodId) {
}
