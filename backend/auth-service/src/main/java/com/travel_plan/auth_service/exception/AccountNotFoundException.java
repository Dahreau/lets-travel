package com.travel_plan.auth_service.exception;

import java.util.UUID;

// fix/audit-gaps (troubleshooting.md #41) : levee quand AccountController.deleteByUserId ne
// trouve aucun compte pour ce userId - traite comme non-bloquant cote appelant (voir
// AuthServiceClient.deleteAccountByUserId cote user-service, qui absorbe ce 404).
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID userId) {
        super("Compte introuvable pour l'utilisateur : " + userId);
    }
}
