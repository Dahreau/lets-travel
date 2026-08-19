package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Transportation;
import com.travel_plan.travel_service.domain.TransportationType;
import java.time.Instant;
import java.util.UUID;

public record TransportationResponse(
        UUID id,
        TransportationType type,
        String fromLocation,
        String toLocation,
        Instant departureTime,
        Instant arrivalTime,
        String provider) {

    public static TransportationResponse from(Transportation transportation) {
        return new TransportationResponse(
                transportation.getId(),
                transportation.getType(),
                transportation.getFromLocation(),
                transportation.getToLocation(),
                transportation.getDepartureTime(),
                transportation.getArrivalTime(),
                transportation.getProvider());
    }
}
