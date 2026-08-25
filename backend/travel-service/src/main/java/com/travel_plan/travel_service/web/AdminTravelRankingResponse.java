package com.travel_plan.travel_service.web;

import java.math.BigDecimal;
import java.util.UUID;

// Classement Admin des voyages par revenu estime (docs/lets-travel_project.md, section Admin).
public record AdminTravelRankingResponse(
        UUID travelId,
        String title,
        UUID managerId,
        long activeSubscriberCount,
        BigDecimal revenue,
        Double averageRating) {
}
