package com.travel_plan.travel_service.web;

import java.math.BigDecimal;
import java.util.List;

// estimatedRevenue = prix x abonnes actifs par voyage, somme - pas une reconciliation avec les
// vrais paiements de payment-service.
public record ManagerStatsResponse(
        long travelCount, long travelerCount, BigDecimal estimatedRevenue, List<ManagerTravelStatsEntry> travels) {
}
