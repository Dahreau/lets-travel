package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.exception.InvalidTravelRequestException;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.SubscriptionResponse;
import com.travel_plan.travel_service.web.TravelerStatsResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TravelerStatsServiceTest {

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final TravelerStatsService travelerStatsService =
            new TravelerStatsService(subscriptionRepository, feedbackRepository, reportRepository);

    private final UUID travelerId = UUID.randomUUID();
    private final AuthenticatedUser traveler = new AuthenticatedUser("traveler1", "TRAVELER", travelerId);

    @Test
    void myStatsAggregatesCountsForConnectedTraveler() {
        when(subscriptionRepository.countByTravelerId(travelerId)).thenReturn(5L);
        when(subscriptionRepository.countByTravelerIdAndStatus(travelerId, SubscriptionStatus.CANCELLED))
                .thenReturn(1L);
        when(feedbackRepository.countByTravelerId(travelerId)).thenReturn(3L);
        when(reportRepository.countByReporterId(travelerId)).thenReturn(2L);

        TravelerStatsResponse response = travelerStatsService.myStats(traveler);

        assertThat(response.participationCount()).isEqualTo(5);
        assertThat(response.feedbackCount()).isEqualTo(3);
        assertThat(response.reportCount()).isEqualTo(2);
        assertThat(response.cancellationCount()).isEqualTo(1);
    }

    @Test
    void myStatsThrowsWhenCallerHasNoLinkedProfile() {
        AuthenticatedUser adminWithoutProfile = new AuthenticatedUser("admin", "ADMIN", null);

        assertThatThrownBy(() -> travelerStatsService.myStats(adminWithoutProfile))
                .isInstanceOf(InvalidTravelRequestException.class);
    }

    @Test
    void mySubscriptionsReturnsConnectedTravelerHistoryMostRecentFirst() {
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .travel(Travel.builder().id(UUID.randomUUID()).build())
                .travelerId(travelerId)
                .status(SubscriptionStatus.ACTIVE)
                .subscribedAt(Instant.now())
                .build();
        when(subscriptionRepository.findByTravelerIdOrderBySubscribedAtDesc(travelerId))
                .thenReturn(List.of(subscription));

        List<SubscriptionResponse> response = travelerStatsService.mySubscriptions(traveler);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(subscription.getId());
    }

    @Test
    void mySubscriptionsThrowsWhenCallerHasNoLinkedProfile() {
        AuthenticatedUser adminWithoutProfile = new AuthenticatedUser("admin", "ADMIN", null);

        assertThatThrownBy(() -> travelerStatsService.mySubscriptions(adminWithoutProfile))
                .isInstanceOf(InvalidTravelRequestException.class);
    }
}
