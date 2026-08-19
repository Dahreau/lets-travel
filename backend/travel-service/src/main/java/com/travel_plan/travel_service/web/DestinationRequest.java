package com.travel_plan.travel_service.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record DestinationRequest(
        @NotBlank String city,
        @NotBlank String country,
        @NotNull LocalDate arrivalDate,
        @NotNull LocalDate departureDate,
        @NotNull Integer orderIndex,
        List<@Valid ActivityRequest> activities,
        @Valid AccommodationRequest accommodation) {

    @AssertTrue(message = "departureDate must not be before arrivalDate")
    public boolean isDateRangeValid() {
        return arrivalDate == null || departureDate == null || !departureDate.isBefore(arrivalDate);
    }
}
