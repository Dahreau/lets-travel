package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.exception.DuplicateFeedbackException;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidFeedbackRequestException;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.FeedbackRequest;
import com.travel_plan.travel_service.web.FeedbackResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedbackServiceTest {

    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final TravelRepository travelRepository = mock(TravelRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);
    private final FeedbackService feedbackService =
            new FeedbackService(feedbackRepository, subscriptionRepository, travelRepository, clock);

    private final UUID travelId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID travelerId = UUID.randomUUID();
    private final AuthenticatedUser traveler = new AuthenticatedUser("traveler1", "TRAVELER", travelerId);
    private final AuthenticatedUser owningManager = new AuthenticatedUser("manager1", "TRAVEL_MANAGER", managerId);
    private final AuthenticatedUser otherManager =
            new AuthenticatedUser("manager2", "TRAVEL_MANAGER", UUID.randomUUID());
    private final AuthenticatedUser admin = new AuthenticatedUser("admin", "ADMIN", null);

    @Test
    void submitCreatesFeedbackWhenParticipatedAndTravelEnded() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, travelerId)).thenReturn(true);
        when(feedbackRepository.findByTravel_IdAndTravelerId(travelId, travelerId)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            feedback.setId(UUID.randomUUID());
            return feedback;
        });

        FeedbackResponse response = feedbackService.submit(travelId, new FeedbackRequest(4, "Great trip"), traveler);

        assertThat(response.travelId()).isEqualTo(travelId);
        assertThat(response.travelerId()).isEqualTo(travelerId);
        assertThat(response.rating()).isEqualTo(4);
    }

    @Test
    void submitThrowsWhenTravelMissing() {
        when(travelRepository.findById(travelId)).thenReturn(Optional.empty());
        FeedbackRequest request = new FeedbackRequest(4, null);

        assertThatThrownBy(() -> feedbackService.submit(travelId, request, traveler))
                .isInstanceOf(TravelNotFoundException.class);
    }

    @Test
    void submitThrowsWhenCallerDidNotParticipate() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, travelerId)).thenReturn(false);
        FeedbackRequest request = new FeedbackRequest(4, null);

        assertThatThrownBy(() -> feedbackService.submit(travelId, request, traveler))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void submitThrowsWhenTravelNotYetEnded() {
        Travel travel = travelEndingIn(5);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, travelerId)).thenReturn(true);
        FeedbackRequest request = new FeedbackRequest(4, null);

        assertThatThrownBy(() -> feedbackService.submit(travelId, request, traveler))
                .isInstanceOf(InvalidFeedbackRequestException.class);
    }

    @Test
    void submitThrowsWhenAlreadySubmitted() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, travelerId)).thenReturn(true);
        when(feedbackRepository.findByTravel_IdAndTravelerId(travelId, travelerId))
                .thenReturn(Optional.of(Feedback.builder().build()));
        FeedbackRequest request = new FeedbackRequest(4, null);

        assertThatThrownBy(() -> feedbackService.submit(travelId, request, traveler))
                .isInstanceOf(DuplicateFeedbackException.class);
    }

    @Test
    void submitThrowsWhenCallerHasNoLinkedUserId() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        FeedbackRequest request = new FeedbackRequest(4, null);

        assertThatThrownBy(() -> feedbackService.submit(travelId, request, admin))
                .isInstanceOf(InvalidFeedbackRequestException.class);
    }

    @Test
    void listForTravelByOwningManagerReturnsFeedback() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(feedbackRepository.findByTravel_Id(travelId))
                .thenReturn(List.of(feedback(travel, travelerId)));

        List<FeedbackResponse> feedback = feedbackService.listForTravel(travelId, owningManager);

        assertThat(feedback).hasSize(1);
    }

    @Test
    void listForTravelByNonOwningManagerIsForbidden() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

        assertThatThrownBy(() -> feedbackService.listForTravel(travelId, otherManager))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listForTravelByTravelerIsForbidden() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

        assertThatThrownBy(() -> feedbackService.listForTravel(travelId, traveler))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listForTravelByAdminIsAllowed() {
        Travel travel = travelEndedDaysAgo(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(feedbackRepository.findByTravel_Id(travelId)).thenReturn(List.of());

        assertThat(feedbackService.listForTravel(travelId, admin)).isEmpty();
    }

    private Travel travelEndedDaysAgo(int daysAgo) {
        return Travel.builder()
                .id(travelId)
                .title("Iberian tour")
                .managerId(managerId)
                .startDate(LocalDate.now(clock).minusDays(daysAgo + 7))
                .endDate(LocalDate.now(clock).minusDays(daysAgo))
                .status(TravelStatus.PLANNED)
                .build();
    }

    private Travel travelEndingIn(int daysFromNow) {
        return Travel.builder()
                .id(travelId)
                .title("Iberian tour")
                .managerId(managerId)
                .startDate(LocalDate.now(clock))
                .endDate(LocalDate.now(clock).plusDays(daysFromNow))
                .status(TravelStatus.PLANNED)
                .build();
    }

    private Feedback feedback(Travel travel, UUID travelerId) {
        return Feedback.builder()
                .id(UUID.randomUUID())
                .travel(travel)
                .travelerId(travelerId)
                .rating(5)
                .createdAt(Instant.now(clock))
                .build();
    }
}
