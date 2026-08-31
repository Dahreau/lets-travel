package com.travel_plan.auth_service.exception;

// Levee par AuthController.register avant l'insertion, plutot que de laisser la contrainte
// unique username remonter en DataIntegrityViolationException - voir troubleshooting.md.
public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
