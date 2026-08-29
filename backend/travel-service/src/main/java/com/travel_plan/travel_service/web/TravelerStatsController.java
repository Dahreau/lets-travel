package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.TravelerStatsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// feat/traveler-frontend : couvert par la regle generique GET /api/travels/** de SecurityConfig
// (TRAVELER minimum) - pas de regle dediee necessaire, ces routes sont deja privees au caller
// (verifie dans TravelerStatsService, pas seulement via l'URL).
@RestController
@RequestMapping("/api/travels/travelers")
@RequiredArgsConstructor
public class TravelerStatsController {

    private final TravelerStatsService travelerStatsService;

    @GetMapping("/me/stats")
    public TravelerStatsResponse myStats(Authentication authentication) {
        return travelerStatsService.myStats(principal(authentication));
    }

    @GetMapping("/me/subscriptions")
    public List<SubscriptionResponse> mySubscriptions(Authentication authentication) {
        return travelerStatsService.mySubscriptions(principal(authentication));
    }

    // fix/audit-gaps (troubleshooting.md #40) : voir TravelerStatsService.myFeedbacks/myReports.
    @GetMapping("/me/feedbacks")
    public List<FeedbackResponse> myFeedbacks(Authentication authentication) {
        return travelerStatsService.myFeedbacks(principal(authentication));
    }

    @GetMapping("/me/reports")
    public List<ReportResponse> myReports(Authentication authentication) {
        return travelerStatsService.myReports(principal(authentication));
    }

    private AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
