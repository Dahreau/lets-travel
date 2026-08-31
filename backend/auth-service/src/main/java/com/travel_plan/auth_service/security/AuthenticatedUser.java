package com.travel_plan.auth_service.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

// voir troubleshooting.md #41 - porte userId pour que deleteByUserId distingue self-service
// d'un appel ADMIN ; implemente AuthenticatedPrincipal pour garder getName()==username.
public record AuthenticatedUser(String username, String role, UUID userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
