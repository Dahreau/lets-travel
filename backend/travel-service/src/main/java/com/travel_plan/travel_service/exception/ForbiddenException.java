package com.travel_plan.travel_service.exception;

// Levee quand un Travel Manager authentifie tente de modifier/supprimer un
// voyage qui n'est pas le sien. Un ADMIN n'est jamais concerne (bypass total).
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
