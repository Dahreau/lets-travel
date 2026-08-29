package com.travel_plan.user_service.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

// fix/audit-gaps (troubleshooting.md #41) : meme pattern que travel-service.AuthenticatedUser -
// user-service etait le seul service a n'exposer QUE le username/role dans le principal, sans
// userId. Necessaire pour les nouveaux endpoints self-service GET/DELETE /api/users/me (l'appelant
// doit pouvoir resoudre SON PROPRE profil sans jamais recevoir d'id en parametre - meme pattern
// de garde que TravelerStatsService.requireTravelerId cote travel-service).
// Implemente AuthenticatedPrincipal (pas juste un record nu) pour que Authentication.getName()
// continue de renvoyer le username, comme avant quand le principal etait une simple String.
public record AuthenticatedUser(String username, String role, UUID userId) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}
