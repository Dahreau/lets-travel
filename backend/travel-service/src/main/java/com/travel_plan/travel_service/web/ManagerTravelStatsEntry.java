package com.travel_plan.travel_service.web;

import java.util.UUID;

public record ManagerTravelStatsEntry(
        UUID travelId, String title, long subscriberCount, Double averageRating, long feedbackCount) {
}
