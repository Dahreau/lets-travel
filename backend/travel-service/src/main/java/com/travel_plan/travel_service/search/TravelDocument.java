package com.travel_plan.travel_service.search;

import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Instantane denormalise de Travel, resynchronise a chaque create/update/delete (voir TravelService).
public record TravelDocument(
        String id,
        String title,
        List<String> cities,
        List<String> countries,
        TravelStatus status,
        BigDecimal price,
        String currency,
        LocalDate startDate,
        LocalDate endDate) {

    public static TravelDocument from(Travel travel) {
        List<String> cities =
                travel.getDestinations().stream().map(Destination::getCity).toList();
        List<String> countries = travel.getDestinations().stream()
                .map(Destination::getCountry)
                .distinct()
                .toList();
        return new TravelDocument(
                travel.getId().toString(),
                travel.getTitle(),
                cities,
                countries,
                travel.getStatus(),
                travel.getPrice(),
                travel.getCurrency(),
                travel.getStartDate(),
                travel.getEndDate());
    }
}
