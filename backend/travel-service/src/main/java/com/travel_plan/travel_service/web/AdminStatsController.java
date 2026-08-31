package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.service.AdminStatsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Reserve a l'Admin (voir SecurityConfig) - vue d'ensemble globale, tous managers/voyages confondus.
@RestController
@RequestMapping("/api/travels/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/manager-rankings")
    public List<AdminManagerRankingResponse> managerRankings() {
        return adminStatsService.managerRankings();
    }

    @GetMapping("/travel-rankings")
    public List<AdminTravelRankingResponse> travelRankings() {
        return adminStatsService.travelRankings();
    }

    @GetMapping("/monthly-revenue")
    public List<AdminMonthlyRevenueResponse> monthlyRevenue() {
        return adminStatsService.monthlyRevenue();
    }
}
