package com.travel_plan.auth_service.exception;

public class InvalidRegistrationTokenException extends RuntimeException {

    public InvalidRegistrationTokenException(String message) {
        super(message);
    }
}
