package com.travel_plan.auth_service.exception;

// fix/audit-gaps (troubleshooting.md #41) : levee quand l'appelant de
// DELETE /api/auth/accounts/by-user/{userId} n'est ni ADMIN ni le proprietaire de ce userId -
// meme classe de garde que le fix IDOR #38 (un role autorise a appeler la route n'implique pas
// qu'il soit autorise sur CETTE ressource precise).
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
