package com.travel_plan.payment_service.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

// Principal pose par JwtAuthenticationFilter. Implemente AuthenticatedPrincipal
// (pas juste un record nu) pour que Authentication.getName() continue de
// renvoyer le username, comme avant quand le principal etait une simple String.
// Mirroir exact de travel-service.security.AuthenticatedUser (voir ce fichier
// pour le contexte complet) - introduit ici pour que payment-service puisse
// forcer ownerId au userId reel de l'appelant plutot que de lui faire confiance.
public record AuthenticatedUser(String username, String role, UUID userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
