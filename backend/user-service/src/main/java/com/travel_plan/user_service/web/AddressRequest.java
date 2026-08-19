package com.travel_plan.user_service.web;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank String street, @NotBlank String city, @NotBlank String postalCode, @NotBlank String country) {
}
