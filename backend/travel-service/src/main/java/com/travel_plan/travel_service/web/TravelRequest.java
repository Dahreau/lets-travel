package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.TravelStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// managerId n'est pas @NotNull (ignore si TRAVEL_MANAGER, requis pour ADMIN - verifie dans
// TravelService). price/currency obligatoires ici, contrairement a l'entite Travel (nullable, legacy).
public record TravelRequest(
        @NotBlank String title,
        UUID managerId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull TravelStatus status,
        @NotNull @Positive BigDecimal price,
        @NotBlank String currency,
        @NotEmpty List<@Valid DestinationRequest> destinations,
        List<@Valid TransportationRequest> transportations) {

    @AssertTrue(message = "endDate ne doit pas etre anterieure a startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
