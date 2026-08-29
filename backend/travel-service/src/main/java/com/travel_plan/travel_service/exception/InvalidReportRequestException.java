package com.travel_plan.travel_service.exception;

// Levee quand un signalement est incoherent : mauvaise cible (manager/traveler), auto-signalement,
// ou appelant sans userId lie.
public class InvalidReportRequestException extends RuntimeException {

    public InvalidReportRequestException(String message) {
        super(message);
    }
}
