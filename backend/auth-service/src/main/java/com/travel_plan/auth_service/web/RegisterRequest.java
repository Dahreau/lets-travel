package com.travel_plan.auth_service.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// 2e etape de l'inscription publique traveler (feat/traveler-experience) : userId doit venir
// de la reponse de POST /api/users/register (user-service), appelee en 1er par le client.
// Pas de verification cross-service que ce userId existe reellement / pointe vers un TRAVELER -
// meme niveau de confiance que CreateAccountRequest (admin-only) qui ne verifie deja pas non
// plus son userId aupres de user-service. role toujours force a TRAVELER cote AuthController.
public record RegisterRequest(@NotBlank String username, @NotBlank String password, @NotNull UUID userId) {
}
