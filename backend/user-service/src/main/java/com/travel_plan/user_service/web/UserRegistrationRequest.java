package com.travel_plan.user_service.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Inscription publique : pas de champ "role", toujours force a TRAVELER (UserService.register()).
// Voir docs/nouveautes-vs-travel-plan.md pour le flux d'inscription en 2 etapes.
public record UserRegistrationRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        String phone,
        @Valid AddressRequest address,
        // voir troubleshooting.md #41 - @AssertTrue rejette (400) toute inscription sans consentement
        // coche (case a cocher register.html, jamais cochee par defaut).
        @AssertTrue(message = "Vous devez accepter la politique de confidentialite pour vous inscrire") boolean acceptedPrivacyPolicy) {
}
