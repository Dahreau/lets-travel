package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id, UUID travelId, UUID travelerId, SubscriptionStatus status, Instant subscribedAt, Instant cancelledAt) {

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getTravel().getId(),
                subscription.getTravelerId(),
                subscription.getStatus(),
                subscription.getSubscribedAt(),
                subscription.getCancelledAt());
    }
}
