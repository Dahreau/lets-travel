package com.travel_plan.travel_service.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

// Principal pose par JwtAuthenticationFilter. Implemente AuthenticatedPrincipal
// (pas juste un record nu) pour que Authentication.getName() continue de
// renvoyer le username, comme avant quand le principal etait une simple String.
public record AuthenticatedUser(String username, String role, UUID userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
