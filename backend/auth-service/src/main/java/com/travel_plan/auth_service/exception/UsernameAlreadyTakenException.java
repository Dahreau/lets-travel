package com.travel_plan.auth_service.exception;

// Levee par AuthController.register avant l'insertion (plutot que de laisser la contrainte
// unique sur accounts.username remonter en DataIntegrityViolationException non geree ici -
// voir troubleshooting.md).
public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
