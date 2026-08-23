package com.travel_plan.travel_service.exception;

// Levee quand un traveler tente de laisser un 2e feedback sur le meme travel - un seul
// avis par (travel, traveler), voir aussi l'index unique en base (migration V5).
public class DuplicateFeedbackException extends RuntimeException {

    public DuplicateFeedbackException(String message) {
        super(message);
    }
}
