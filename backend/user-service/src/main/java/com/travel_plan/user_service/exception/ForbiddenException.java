package com.travel_plan.user_service.exception;

// fix/audit-gaps : levee quand un Travel Manager tente de consulter le profil d'un utilisateur
// qui n'est abonne a aucun de ses voyages (troubleshooting.md #38, IDOR sur GET /api/users/{id}).
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
