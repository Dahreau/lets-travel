package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.web.AdminManagerRankingResponse;
import com.travel_plan.travel_service.web.AdminMonthlyRevenueResponse;
import com.travel_plan.travel_service.web.AdminTravelRankingResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminStatsServiceTest {

    private final TravelRepository travelRepository = mock(TravelRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);
    private final AdminStatsService adminStatsService = new AdminStatsService(
            travelRepository, subscriptionRepository, feedbackRepository, reportRepository, clock);

    private final UUID managerA = UUID.randomUUID();
    private final UUID managerB = UUID.randomUUID();

    @Test
    void managerRankingsOrdersByPerformanceScoreDescending() {
        Travel travelA = travel(managerA, BigDecimal.valueOf(100));
        Travel travelB = travel(managerB, BigDecimal.valueOf(100));
        when(travelRepository.findAll()).thenReturn(List.of(travelA, travelB));
        when(subscriptionRepository.countByTravel_IdAndStatus(travelA.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(2L);
        when(subscriptionRepository.countByTravel_IdAndStatus(travelB.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(2L);
        when(subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerA, SubscriptionStatus.ACTIVE))
                .thenReturn(2L);
        when(subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerB, SubscriptionStatus.ACTIVE))
                .thenReturn(2L);
        // Meme revenu (100 x 2) pour A et B : seule la note moyenne les depage, A doit donc arriver en tete.
        when(feedbackRepository.findByTravel_ManagerId(managerA)).thenReturn(List.of(feedbackWithRating(5)));
        when(feedbackRepository.findByTravel_ManagerId(managerB)).thenReturn(List.of(feedbackWithRating(1)));
        when(reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerA))
                .thenReturn(0L);
        when(reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerB))
                .thenReturn(0L);

        List<AdminManagerRankingResponse> rankings = adminStatsService.managerRankings();

        assertThat(rankings).hasSize(2);
        assertThat(rankings.get(0).managerId()).isEqualTo(managerA);
        assertThat(rankings.get(0).performanceScore()).isGreaterThan(rankings.get(1).performanceScore());
    }

    @Test
    void managerRankingsPenalizesReportsAndReturnsNullRatingWithoutFeedback() {
        Travel solo = travel(managerA, null);
        when(travelRepository.findAll()).thenReturn(List.of(solo));
        when(subscriptionRepository.countByTravel_IdAndStatus(solo.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(0L);
        when(subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerA, SubscriptionStatus.ACTIVE))
                .thenReturn(0L);
        when(feedbackRepository.findByTravel_ManagerId(managerA)).thenReturn(List.of());
        when(reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerA))
                .thenReturn(2L);

        AdminManagerRankingResponse ranking = adminStatsService.managerRankings().get(0);

        assertThat(ranking.averageRating()).isNull();
        assertThat(ranking.estimatedRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        // 0 (pas de note) + 0 (pas de revenu) - 2 x 5 (signalements) = -10.
        assertThat(ranking.performanceScore()).isEqualTo(-10.0);
    }

    @Test
    void travelRankingsOrdersByRevenueDescending() {
        Travel cheap = travel(managerA, BigDecimal.valueOf(50));
        Travel expensive = travel(managerA, BigDecimal.valueOf(200));
        when(travelRepository.findAll()).thenReturn(List.of(cheap, expensive));
        when(subscriptionRepository.countByTravel_IdAndStatus(cheap.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(1L);
        when(subscriptionRepository.countByTravel_IdAndStatus(expensive.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(1L);
        when(feedbackRepository.findByTravel_Id(cheap.getId())).thenReturn(List.of());
        when(feedbackRepository.findByTravel_Id(expensive.getId())).thenReturn(List.of());

        List<AdminTravelRankingResponse> rankings = adminStatsService.travelRankings();

        assertThat(rankings.get(0).travelId()).isEqualTo(expensive.getId());
        assertThat(rankings.get(0).revenue()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    private Travel travel(UUID managerId, BigDecimal price) {
        return Travel.builder()
                .id(UUID.randomUUID())
                .title("Trip")
                .managerId(managerId)
                .startDate(LocalDate.now().plusDays(30))
                .endDate(LocalDate.now().plusDays(37))
                .status(TravelStatus.CONFIRMED)
                .price(price)
                .currency(price == null ? null : "EUR")
                .build();
    }

    private Feedback feedbackWithRating(int rating) {
        return Feedback.builder().id(UUID.randomUUID()).rating(rating).build();
    }

    @Test
    void monthlyRevenueSumsActiveSubscriptionsIntoTheirSubscribedMonthOverTheLastSixMonths() {
        Travel travel = travel(managerA, BigDecimal.valueOf(100));
        Subscription august = subscription(travel, Instant.parse("2026-08-01T00:00:00Z"));
        Subscription july = subscription(travel, Instant.parse("2026-07-15T00:00:00Z"));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(august, july));

        List<AdminMonthlyRevenueResponse> revenue = adminStatsService.monthlyRevenue();

        assertThat(revenue).hasSize(6);
        assertThat(revenue.get(5).month()).isEqualTo("2026-08");
        assertThat(revenue.get(5).revenue()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(revenue.get(4).month()).isEqualTo("2026-07");
        assertThat(revenue.get(4).revenue()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(revenue.get(0).revenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Subscription subscription(Travel travel, Instant subscribedAt) {
        return Subscription.builder()
                .id(UUID.randomUUID())
                .travel(travel)
                .travelerId(UUID.randomUUID())
                .status(SubscriptionStatus.ACTIVE)
                .subscribedAt(subscribedAt)
                .build();
    }
}
