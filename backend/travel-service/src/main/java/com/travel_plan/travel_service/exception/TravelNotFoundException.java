package com.travel_plan.travel_service.exception;

import java.util.UUID;

public class TravelNotFoundException extends RuntimeException {

    public TravelNotFoundException(UUID id) {
        super("Travel not found: " + id);
    }
}
