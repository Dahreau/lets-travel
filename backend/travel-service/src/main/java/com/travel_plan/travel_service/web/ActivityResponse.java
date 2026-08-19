package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Activity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ActivityResponse(UUID id, String name, String description, LocalDate date, BigDecimal cost) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(
                activity.getId(), activity.getName(), activity.getDescription(), activity.getDate(),
                activity.getCost());
    }
}
