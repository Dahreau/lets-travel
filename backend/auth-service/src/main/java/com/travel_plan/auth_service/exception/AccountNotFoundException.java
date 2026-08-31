package com.travel_plan.auth_service.exception;

import java.util.UUID;

// voir troubleshooting.md #41 - levee si deleteByUserId ne trouve aucun compte ; le 404 est
// absorbe cote appelant (AuthServiceClient.deleteAccountByUserId).
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID userId) {
        super("Compte introuvable pour l'utilisateur : " + userId);
    }
}
