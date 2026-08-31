package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidTravelRequestException;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelFeedbackAggregate;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.repository.TravelSubscriberCount;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.ManagerPublicStatsResponse;
import com.travel_plan.travel_service.web.ManagerPublicTravelRatingEntry;
import com.travel_plan.travel_service.web.ManagerStatsResponse;
import com.travel_plan.travel_service.web.ManagerTravelStatsEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManagerStatsServiceTest {

    private final TravelRepository travelRepository = mock(TravelRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ManagerStatsService managerStatsService =
            new ManagerStatsService(travelRepository, subscriptionRepository, feedbackRepository, reportRepository);

    private final UUID managerId = UUID.randomUUID();
    private final AuthenticatedUser manager = new AuthenticatedUser("manager1", "TRAVEL_MANAGER", managerId);
    private final AuthenticatedUser traveler = new AuthenticatedUser("traveler1", "TRAVELER", UUID.randomUUID());
    private final AuthenticatedUser admin = new AuthenticatedUser("admin", "ADMIN", null);

    @Test
    void myStatsSumsEstimatedRevenueAcrossOwnPricedTravels() {
        Travel priced = travel(BigDecimal.valueOf(100));
        Travel free = travel(null);
        when(travelRepository.findByManagerId(managerId)).thenReturn(List.of(priced, free));
        List<TravelSubscriberCount> counts =
                List.of(subscriberCount(priced.getId(), 3L), subscriberCount(free.getId(), 5L));
        when(subscriptionRepository.countActiveSubscribersGroupedByTravelIds(anyList(), eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(counts);
        when(subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerId, SubscriptionStatus.ACTIVE))
                .thenReturn(7L);

        ManagerStatsResponse response = managerStatsService.myStats(manager);

        assertThat(response.travelCount()).isEqualTo(2);
        assertThat(response.travelerCount()).isEqualTo(7);
        // free n'a pas de prix : ses 5 abonnes ne contribuent rien au revenu, seul priced compte (100 x 3).
        assertThat(response.estimatedRevenue()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void myStatsIncludesPerTravelSubscriberCountAndAverageRating() {
        Travel travel = travel(BigDecimal.valueOf(100));
        when(travelRepository.findByManagerId(managerId)).thenReturn(List.of(travel));
        List<TravelSubscriberCount> counts = List.of(subscriberCount(travel.getId(), 4L));
        when(subscriptionRepository.countActiveSubscribersGroupedByTravelIds(anyList(), eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(counts);
        when(subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerId, SubscriptionStatus.ACTIVE))
                .thenReturn(4L);
        List<TravelFeedbackAggregate> aggregates = List.of(feedbackAggregate(travel.getId(), 3.0, 2));
        when(feedbackRepository.aggregateByTravelIds(anyList())).thenReturn(aggregates);

        ManagerStatsResponse response = managerStatsService.myStats(manager);

        assertThat(response.travels()).containsExactly(
                new ManagerTravelStatsEntry(travel.getId(), travel.getTitle(), 4L, 3.0, 2));
    }

    @Test
    void myStatsReturnsNullAverageRatingForTravelWithNoFeedbackYet() {
        Travel travel = travel(BigDecimal.valueOf(100));
        when(travelRepository.findByManagerId(managerId)).thenReturn(List.of(travel));

        ManagerStatsResponse response = managerStatsService.myStats(manager);

        assertThat(response.travels()).hasSize(1);
        assertThat(response.travels().get(0).averageRating()).isNull();
    }

    @Test
    void myStatsReturnsZeroesWhenManagerHasNoTravels() {
        when(travelRepository.findByManagerId(managerId)).thenReturn(List.of());
        when(subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerId, SubscriptionStatus.ACTIVE))
                .thenReturn(0L);

        ManagerStatsResponse response = managerStatsService.myStats(manager);

        assertThat(response.travelCount()).isZero();
        assertThat(response.travelerCount()).isZero();
        assertThat(response.estimatedRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void myStatsThrowsForbiddenForNonManagerCaller() {
        assertThatThrownBy(() -> managerStatsService.myStats(traveler)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> managerStatsService.myStats(admin)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void myStatsThrowsWhenManagerHasNoLinkedProfile() {
        AuthenticatedUser managerWithoutProfile = new AuthenticatedUser("ghost", "TRAVEL_MANAGER", null);

        assertThatThrownBy(() -> managerStatsService.myStats(managerWithoutProfile))
                .isInstanceOf(InvalidTravelRequestException.class);
    }

    @Test
    void publicStatsAveragesFeedbackRatingsAndCountsReportsAgainstThisManager() {
        when(travelRepository.countByManagerId(managerId)).thenReturn(4L);
        when(feedbackRepository.findByTravel_ManagerId(managerId))
                .thenReturn(List.of(feedbackWithRating(4), feedbackWithRating(2)));
        when(reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerId))
                .thenReturn(1L);

        ManagerPublicStatsResponse response = managerStatsService.publicStats(managerId);

        assertThat(response.travelCount()).isEqualTo(4);
        assertThat(response.averageRating()).isEqualTo(3.0);
        assertThat(response.reportCount()).isEqualTo(1);
    }

    @Test
    void publicStatsReturnsNullAverageRatingWhenNoFeedbackYet() {
        when(travelRepository.countByManagerId(managerId)).thenReturn(0L);
        when(feedbackRepository.findByTravel_ManagerId(managerId)).thenReturn(List.of());
        when(reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerId))
                .thenReturn(0L);

        ManagerPublicStatsResponse response = managerStatsService.publicStats(managerId);

        assertThat(response.averageRating()).isNull();
    }

    @Test
    void publicStatsIncludesPerTravelRatingBreakdown() {
        Travel travel1 = travel(BigDecimal.valueOf(100));
        Travel travel2 = travel(BigDecimal.valueOf(200));
        when(travelRepository.countByManagerId(managerId)).thenReturn(2L);
        when(travelRepository.findByManagerId(managerId)).thenReturn(List.of(travel1, travel2));
        when(feedbackRepository.findByTravel_ManagerId(managerId)).thenReturn(List.of(feedbackWithRating(4)));
        List<TravelFeedbackAggregate> aggregates = List.of(feedbackAggregate(travel1.getId(), 4.5, 2));
        when(feedbackRepository.aggregateByTravelIds(List.of(travel1.getId(), travel2.getId())))
                .thenReturn(aggregates);
        when(reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerId))
                .thenReturn(0L);

        ManagerPublicStatsResponse response = managerStatsService.publicStats(managerId);

        assertThat(response.travelRatings()).extracting(ManagerPublicTravelRatingEntry::travelId)
                .containsExactly(travel1.getId(), travel2.getId());
        assertThat(response.travelRatings().get(0).averageRating()).isEqualTo(4.5);
        assertThat(response.travelRatings().get(0).feedbackCount()).isEqualTo(2);
        assertThat(response.travelRatings().get(1).averageRating()).isNull();
        assertThat(response.travelRatings().get(1).feedbackCount()).isZero();
    }

    @Test
    void isMySubscriberReturnsTrueWhenTravelerIsSubscribedToOneOfManagersTravels() {
        UUID travelerId = UUID.randomUUID();
        when(subscriptionRepository.existsByTravel_ManagerIdAndTravelerId(managerId, travelerId)).thenReturn(true);

        assertThat(managerStatsService.isMySubscriber(manager, travelerId)).isTrue();
    }

    @Test
    void isMySubscriberReturnsFalseWhenTravelerIsNotSubscribed() {
        UUID travelerId = UUID.randomUUID();
        when(subscriptionRepository.existsByTravel_ManagerIdAndTravelerId(managerId, travelerId)).thenReturn(false);

        assertThat(managerStatsService.isMySubscriber(manager, travelerId)).isFalse();
    }

    @Test
    void isMySubscriberThrowsForbiddenForNonManagerCaller() {
        UUID travelerId = UUID.randomUUID();

        assertThatThrownBy(() -> managerStatsService.isMySubscriber(traveler, travelerId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> managerStatsService.isMySubscriber(admin, travelerId))
                .isInstanceOf(ForbiddenException.class);
    }

    private Travel travel(BigDecimal price) {
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

    private TravelSubscriberCount subscriberCount(UUID travelId, long activeCount) {
        TravelSubscriberCount projection = mock(TravelSubscriberCount.class);
        when(projection.getTravelId()).thenReturn(travelId);
        when(projection.getActiveCount()).thenReturn(activeCount);
        return projection;
    }

    private TravelFeedbackAggregate feedbackAggregate(UUID travelId, Double averageRating, long feedbackCount) {
        TravelFeedbackAggregate projection = mock(TravelFeedbackAggregate.class);
        when(projection.getTravelId()).thenReturn(travelId);
        when(projection.getAverageRating()).thenReturn(averageRating);
        when(projection.getFeedbackCount()).thenReturn(feedbackCount);
        return projection;
    }
}
