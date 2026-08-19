package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Accommodation;
import com.travel_plan.travel_service.domain.AccommodationType;
import java.time.LocalDate;
import java.util.UUID;

public record AccommodationResponse(
        UUID id, String name, AccommodationType type, String address, LocalDate checkIn, LocalDate checkOut) {

    public static AccommodationResponse from(Accommodation accommodation) {
        return new AccommodationResponse(
                accommodation.getId(),
                accommodation.getName(),
                accommodation.getType(),
                accommodation.getAddress(),
                accommodation.getCheckIn(),
                accommodation.getCheckOut());
    }
}
