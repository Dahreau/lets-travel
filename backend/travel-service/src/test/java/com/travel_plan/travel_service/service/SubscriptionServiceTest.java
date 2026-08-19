package com.travel_plan.travel_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.domain.TravelStatus;
import com.travel_plan.travel_service.exception.DuplicateSubscriptionException;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidSubscriptionRequestException;
import com.travel_plan.travel_service.exception.SubscriptionCutoffException;
import com.travel_plan.travel_service.exception.SubscriptionNotFoundException;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.SubscriptionResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionServiceTest {

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final TravelRepository travelRepository = mock(TravelRepository.class);
    // Horloge fixe plutot que Clock.systemUTC() : les tests de cutoff (a J-3 pile) ne dependent plus
    // de l'instant reel d'execution, ce qui evite un flake theorique si le test tourne pile a minuit.
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final SubscriptionService subscriptionService =
            new SubscriptionService(subscriptionRepository, travelRepository, clock);

    private final UUID travelId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID travelerId = UUID.randomUUID();
    private final AuthenticatedUser traveler = new AuthenticatedUser("traveler1", "TRAVELER", travelerId);
    private final AuthenticatedUser owningManager = new AuthenticatedUser("manager1", "TRAVEL_MANAGER", managerId);
    private final AuthenticatedUser otherManager =
            new AuthenticatedUser("manager2", "TRAVEL_MANAGER", UUID.randomUUID());
    private final AuthenticatedUser admin = new AuthenticatedUser("admin", "ADMIN", null);

    @Test
    void subscribeCreatesActiveSubscription() {
        Travel travel = travelDepartingIn(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findByTravel_IdAndTravelerIdAndStatus(
                        travelId, travelerId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setId(UUID.randomUUID());
            return subscription;
        });

        SubscriptionResponse response = subscriptionService.subscribe(travelId, traveler);

        assertThat(response.travelId()).isEqualTo(travelId);
        assertThat(response.travelerId()).isEqualTo(travelerId);
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void subscribeThrowsWhenTravelMissing() {
        when(travelRepository.findById(travelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.subscribe(travelId, traveler))
                .isInstanceOf(TravelNotFoundException.class);
    }

    @Test
    void subscribeThrowsWhenAlreadyActivelySubscribed() {
        Travel travel = travelDepartingIn(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findByTravel_IdAndTravelerIdAndStatus(
                        travelId, travelerId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(Subscription.builder().build()));

        assertThatThrownBy(() -> subscriptionService.subscribe(travelId, traveler))
                .isInstanceOf(DuplicateSubscriptionException.class);
    }

    @Test
    void subscribeThrowsWhenCallerHasNoLinkedUserId() {
        Travel travel = travelDepartingIn(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

        assertThatThrownBy(() -> subscriptionService.subscribe(travelId, admin))
                .isInstanceOf(InvalidSubscriptionRequestException.class);
    }

    @Test
    void unsubscribeByOwningTravelerCancelsSubscription() {
        Travel travel = travelDepartingIn(10);
        Subscription subscription = activeSubscription(travel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        subscriptionService.unsubscribe(travelId, subscription.getId(), traveler);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(subscription.getCancelledAt()).isNotNull();
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void unsubscribeByOwningManagerCancelsSubscription() {
        Travel travel = travelDepartingIn(10);
        Subscription subscription = activeSubscription(travel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        subscriptionService.unsubscribe(travelId, subscription.getId(), owningManager);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void unsubscribeByAdminCancelsSubscription() {
        Travel travel = travelDepartingIn(10);
        Subscription subscription = activeSubscription(travel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        subscriptionService.unsubscribe(travelId, subscription.getId(), admin);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void unsubscribeByNonOwningManagerIsForbidden() {
        Travel travel = travelDepartingIn(10);
        Subscription subscription = activeSubscription(travel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        UUID subscriptionId = subscription.getId();

        assertThatThrownBy(() -> subscriptionService.unsubscribe(travelId, subscriptionId, otherManager))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unsubscribeByAnotherTravelerIsForbidden() {
        Travel travel = travelDepartingIn(10);
        Subscription subscription = activeSubscription(travel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        UUID subscriptionId = subscription.getId();
        AuthenticatedUser anotherTraveler = new AuthenticatedUser("traveler2", "TRAVELER", UUID.randomUUID());

        assertThatThrownBy(() -> subscriptionService.unsubscribe(travelId, subscriptionId, anotherTraveler))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unsubscribeWithinCutoffIsRejected() {
        Travel travel = travelDepartingIn(2);
        Subscription subscription = activeSubscription(travel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        UUID subscriptionId = subscription.getId();

        assertThatThrownBy(() -> subscriptionService.unsubscribe(travelId, subscriptionId, traveler))
                .isInstanceOf(SubscriptionCutoffException.class);
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void unsubscribeExactlyAtCutoffIsAllowed() {
        Travel travel = travelDepartingIn(3);
        Subscription subscription = activeSubscription(travel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        subscriptionService.unsubscribe(travelId, subscription.getId(), traveler);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void unsubscribingAlreadyCancelledSubscriptionIsIdempotent() {
        Travel travel = travelDepartingIn(1);
        Subscription subscription = activeSubscription(travel, travelerId);
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        subscriptionService.unsubscribe(travelId, subscription.getId(), traveler);

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void unsubscribeThrowsWhenSubscriptionBelongsToAnotherTravel() {
        Travel travel = travelDepartingIn(10);
        Travel otherTravel = travelDepartingIn(10);
        otherTravel.setId(UUID.randomUUID());
        Subscription subscription = activeSubscription(otherTravel, travelerId);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        UUID subscriptionId = subscription.getId();

        assertThatThrownBy(() -> subscriptionService.unsubscribe(travelId, subscriptionId, traveler))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void listSubscribersByOwningManagerReturnsSubscribers() {
        Travel travel = travelDepartingIn(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findByTravel_Id(travelId))
                .thenReturn(List.of(activeSubscription(travel, travelerId)));

        List<SubscriptionResponse> subscribers = subscriptionService.listSubscribers(travelId, owningManager);

        assertThat(subscribers).hasSize(1);
    }

    @Test
    void listSubscribersByNonOwningManagerIsForbidden() {
        Travel travel = travelDepartingIn(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));

        assertThatThrownBy(() -> subscriptionService.listSubscribers(travelId, otherManager))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listSubscribersByAdminIsAllowed() {
        Travel travel = travelDepartingIn(10);
        when(travelRepository.findById(travelId)).thenReturn(Optional.of(travel));
        when(subscriptionRepository.findByTravel_Id(travelId)).thenReturn(List.of());

        assertThat(subscriptionService.listSubscribers(travelId, admin)).isEmpty();
    }

    private Travel travelDepartingIn(int daysFromNow) {
        return Travel.builder()
                .id(travelId)
                .title("Iberian tour")
                .managerId(managerId)
                .startDate(LocalDate.now(clock).plusDays(daysFromNow))
                .endDate(LocalDate.now(clock).plusDays(daysFromNow + 7))
                .status(TravelStatus.PLANNED)
                .build();
    }

    private Subscription activeSubscription(Travel travel, UUID subscriberId) {
        return Subscription.builder()
                .id(UUID.randomUUID())
                .travel(travel)
                .travelerId(subscriberId)
                .status(SubscriptionStatus.ACTIVE)
                .subscribedAt(Instant.now(clock))
                .build();
    }
}
