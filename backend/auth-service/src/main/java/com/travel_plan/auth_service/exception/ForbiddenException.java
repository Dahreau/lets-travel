package com.travel_plan.auth_service.exception;

// voir troubleshooting.md #41 - levee si l'appelant n'est ni ADMIN ni proprietaire du userId
// cible (meme classe de garde que le fix IDOR #38).
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
