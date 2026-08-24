package com.travel_plan.travel_service.web;

import java.math.BigDecimal;

// Tableau de bord prive du manager connecte (feat/manager-frontend). estimatedRevenue est une
// estimation (prix x abonnes actifs par voyage, sommee), pas une reconciliation avec les vrais
// paiements de payment-service - voir ManagerStatsService.myStats et
// docs/nouveautes-vs-travel-plan.md pour le raisonnement.
public record ManagerStatsResponse(long travelCount, long travelerCount, BigDecimal estimatedRevenue) {
}
