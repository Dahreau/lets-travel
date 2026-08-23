package com.travel_plan.travel_service.exception;

// Levee quand un feedback est tente avant la fin du voyage (l'enonce parle de "leur
// experience de voyage", donc apres coup) ou par un appelant sans userId lie.
public class InvalidFeedbackRequestException extends RuntimeException {

    public InvalidFeedbackRequestException(String message) {
        super(message);
    }
}
