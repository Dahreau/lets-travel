package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.TravelStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// managerId n'est PAS @NotNull : quand l'appelant est TRAVEL_MANAGER, il est
// force a son propre userId (JWT) et toute valeur envoyee ici est ignoree.
// Seul un appel ADMIN doit le fournir explicitement (verifie dans TravelService,
// pas ici, car ca depend du role de l'appelant, pas juste du contenu de la requete).
public record TravelRequest(
        @NotBlank String title,
        UUID managerId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull TravelStatus status,
        @NotEmpty List<@Valid DestinationRequest> destinations,
        List<@Valid TransportationRequest> transportations) {

    @AssertTrue(message = "endDate must not be before startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
