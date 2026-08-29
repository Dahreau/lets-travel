package com.travel_plan.auth_service.web;

import jakarta.validation.constraints.NotBlank;

// registrationToken est verifie et signe par user-service - voir JwtService.validateRegistrationToken.
public record RegisterRequest(@NotBlank String username, @NotBlank String password, @NotBlank String registrationToken) {
}
