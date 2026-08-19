package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Destination;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DestinationResponse(
        UUID id,
        String city,
        String country,
        LocalDate arrivalDate,
        LocalDate departureDate,
        Integer orderIndex,
        List<ActivityResponse> activities,
        AccommodationResponse accommodation) {

    public static DestinationResponse from(Destination destination) {
        List<ActivityResponse> activities =
                destination.getActivities().stream().map(ActivityResponse::from).toList();
        AccommodationResponse accommodation = destination.getAccommodation() == null
                ? null
                : AccommodationResponse.from(destination.getAccommodation());
        return new DestinationResponse(
                destination.getId(),
                destination.getCity(),
                destination.getCountry(),
                destination.getArrivalDate(),
                destination.getDepartureDate(),
                destination.getOrderIndex(),
                activities,
                accommodation);
    }
}
