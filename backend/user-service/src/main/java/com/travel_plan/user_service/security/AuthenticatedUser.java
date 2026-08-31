package com.travel_plan.user_service.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

// voir troubleshooting.md #41 - principal enrichi avec userId (self-service /api/users/me).
// Implemente AuthenticatedPrincipal pour qu'Authentication.getName() renvoie toujours le username.
public record AuthenticatedUser(String username, String role, UUID userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
