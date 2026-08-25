package com.travel_plan.travel_service.web;

import java.math.BigDecimal;
import java.util.UUID;

// Classement Admin des managers (docs/lets-travel_project.md, section Admin) - voir
// AdminStatsService pour le calcul du performanceScore.
public record AdminManagerRankingResponse(
        UUID managerId,
        long travelCount,
        long travelerCount,
        BigDecimal estimatedRevenue,
        Double averageRating,
        long reportCount,
        double performanceScore) {
}
