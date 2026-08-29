package com.travel_plan.travel_service.repository;

import java.util.UUID;

public interface TravelSubscriberCount {
    UUID getTravelId();

    long getActiveCount();
}
