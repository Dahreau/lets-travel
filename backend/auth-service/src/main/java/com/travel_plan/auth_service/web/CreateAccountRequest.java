package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// userId : obligatoire pour TRAVELER/TRAVEL_MANAGER (pointe vers le User cree
// au prealable dans user-service) ; doit rester absent pour ADMIN, qui n'a pas
// de fiche profil.
public record CreateAccountRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull Role role,
        UUID userId) {

    @AssertTrue(message = "userId est obligatoire pour TRAVELER/TRAVEL_MANAGER et doit etre absent pour ADMIN")
    public boolean isUserIdConsistentWithRole() {
        if (role == null) {
            return true;
        }
        return role == Role.ADMIN ? userId == null : userId != null;
    }
}
