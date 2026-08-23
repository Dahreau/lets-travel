package com.travel_plan.travel_service.exception;

// Levee quand un signalement est incoherent : reportedId ne correspond pas au manager du
// travel (type MANAGER) ou a un autre abonne du meme travel (type TRAVELER), auto-signalement,
// ou appelant sans userId lie.
public class InvalidReportRequestException extends RuntimeException {

    public InvalidReportRequestException(String message) {
        super(message);
    }
}
