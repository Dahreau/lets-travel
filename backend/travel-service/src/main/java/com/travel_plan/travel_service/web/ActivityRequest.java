package com.travel_plan.travel_service.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ActivityRequest(
        @NotBlank String name, String description, @NotNull LocalDate date, @PositiveOrZero BigDecimal cost) {
}
