package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record TravelResponse(
        UUID id,
        String title,
        UUID ownerId,
        LocalDate startDate,
        LocalDate endDate,
        long durationDays,
        TravelStatus status,
        List<DestinationResponse> destinations,
        List<TransportationResponse> transportations,
        Instant createdAt,
        Instant updatedAt) {

    public static TravelResponse from(Travel travel) {
        List<DestinationResponse> destinations = travel.getDestinations().stream()
                .sorted(Comparator.comparing(Destination::getOrderIndex))
                .map(DestinationResponse::from)
                .toList();
        List<TransportationResponse> transportations =
                travel.getTransportations().stream().map(TransportationResponse::from).toList();
        return new TravelResponse(
                travel.getId(),
                travel.getTitle(),
                travel.getOwnerId(),
                travel.getStartDate(),
                travel.getEndDate(),
                travel.getDurationDays(),
                travel.getStatus(),
                destinations,
                transportations,
                travel.getCreatedAt(),
                travel.getUpdatedAt());
    }
}
