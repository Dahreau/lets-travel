package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.domain.Feedback;
import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id, UUID travelId, UUID travelerId, int rating, String comment, Instant createdAt) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getTravel().getId(),
                feedback.getTravelerId(),
                feedback.getRating(),
                feedback.getComment(),
                feedback.getCreatedAt());
    }
}
