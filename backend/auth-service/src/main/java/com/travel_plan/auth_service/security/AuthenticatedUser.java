package com.travel_plan.auth_service.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

// fix/audit-gaps (troubleshooting.md #41) : meme pattern que travel-service.AuthenticatedUser -
// necessaire pour que AccountController.deleteByUserId sache QUI appelle (userId propre, pas
// juste le role) et distingue self-service (userId == caller.userId()) d'un appel ADMIN.
// Implemente AuthenticatedPrincipal (pas juste un record nu) pour que Authentication.getName()
// continue de renvoyer le username, comme avant quand le principal etait une simple String.
public record AuthenticatedUser(String username, String role, UUID userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
