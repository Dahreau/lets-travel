package com.travel_plan.travel_service.web;

import java.math.BigDecimal;

// Revenu estime par mois (6 derniers, fix/audit-gaps) - meme convention "estimee" que
// AdminManagerRankingResponse/AdminTravelRankingResponse (voir AdminStatsService).
public record AdminMonthlyRevenueResponse(String month, BigDecimal revenue) {
}
