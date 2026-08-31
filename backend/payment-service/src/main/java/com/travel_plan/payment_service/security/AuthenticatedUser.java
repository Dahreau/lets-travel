package com.travel_plan.payment_service.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

// Principal pose par JwtAuthenticationFilter ; implemente AuthenticatedPrincipal pour que
// Authentication.getName() renvoie le username. Mirroir de travel-service.security.AuthenticatedUser.
public record AuthenticatedUser(String username, String role, UUID userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
