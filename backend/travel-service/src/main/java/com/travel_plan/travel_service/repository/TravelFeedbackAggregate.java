package com.travel_plan.travel_service.repository;

import java.util.UUID;

public interface TravelFeedbackAggregate {
    UUID getTravelId();

    Double getAverageRating();

    long getFeedbackCount();
}
