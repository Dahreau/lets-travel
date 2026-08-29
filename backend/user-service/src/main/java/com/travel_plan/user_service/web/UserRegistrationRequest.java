package com.travel_plan.user_service.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Inscription publique (feat/traveler-experience) : pas de champ "role" ici, contrairement a
// UserRequest - un inscrit public devient toujours TRAVELER, force cote serveur dans
// UserService.register(). Cree le profil ; l'appelant utilise ensuite l'id renvoye pour creer
// ses identifiants de connexion via POST /api/auth/register (auth-service) - voir
// docs/nouveautes-vs-travel-plan.md pour le detail de ce flux en 2 etapes.
public record UserRegistrationRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        String phone,
        @Valid AddressRequest address,
        // fix/audit-gaps (troubleshooting.md #41) : consentement RGPD obligatoire a l'inscription
        // publique - @AssertTrue rejette (400) toute inscription ou la case n'est pas cochee,
        // avant meme d'atteindre UserService.register(). Case a cocher cote frontend (register.html),
        // jamais cochee par defaut.
        @AssertTrue(message = "Vous devez accepter la politique de confidentialite pour vous inscrire") boolean acceptedPrivacyPolicy) {
}
