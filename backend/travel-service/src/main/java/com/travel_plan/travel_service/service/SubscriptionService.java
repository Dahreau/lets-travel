package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.exception.DuplicateSubscriptionException;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidSubscriptionRequestException;
import com.travel_plan.travel_service.exception.SubscriptionCutoffException;
import com.travel_plan.travel_service.exception.SubscriptionNotFoundException;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.graph.RecommendationSyncService;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.SubscriptionResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String TRAVEL_MANAGER_ROLE = "TRAVEL_MANAGER";
    private static final int CANCELLATION_CUTOFF_DAYS = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final TravelRepository travelRepository;
    private final Clock clock;
    private final RecommendationSyncService recommendationSyncService;

    public SubscriptionResponse subscribe(UUID travelId, AuthenticatedUser caller) {
        Travel travel = getTravelOrThrow(travelId);
        UUID travelerId = requireTravelerId(caller);

        subscriptionRepository
                .findByTravel_IdAndTravelerIdAndStatus(travelId, travelerId, SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new DuplicateSubscriptionException("Already subscribed to this travel");
                });

        Subscription subscription = Subscription.builder()
                .travel(travel)
                .travelerId(travelerId)
                .status(SubscriptionStatus.ACTIVE)
                .subscribedAt(Instant.now(clock))
                .build();

        Subscription saved = subscriptionRepository.save(subscription);
        // feat/search-and-recommendations : un abonnement = un signal "voyage aime" pour le
        // moteur de recommandations base sur le contenu (voir RecommendationRepository).
        recommendationSyncService.recordParticipation(travelerId, travelId);
        return SubscriptionResponse.from(saved);
    }

    public void unsubscribe(UUID travelId, UUID subscriptionId, AuthenticatedUser caller) {
        Travel travel = getTravelOrThrow(travelId);
        Subscription subscription = subscriptionRepository
                .findById(subscriptionId)
                .filter(found -> found.getTravel().getId().equals(travelId))
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));

        requireCancellationRights(travel, subscription, caller);

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            return;
        }

        if (isPastCancellationCutoff(travel.getStartDate())) {
            throw new SubscriptionCutoffException(
                    "Subscriptions can no longer be cancelled less than "
                            + CANCELLATION_CUTOFF_DAYS + " days before departure");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now(clock));
        subscriptionRepository.save(subscription);
        recommendationSyncService.removeParticipation(subscription.getTravelerId(), travelId);
    }

    public List<SubscriptionResponse> listSubscribers(UUID travelId, AuthenticatedUser caller) {
        Travel travel = getTravelOrThrow(travelId);
        requireManagerOwnershipOrAdmin(travel, caller);

        return subscriptionRepository.findByTravel_Id(travelId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    // Un compte ADMIN par defaut n'a pas de fiche User (userId null) : il ne peut pas
    // etre "le" traveler d'un abonnement, il n'y a rien a rattacher.
    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidSubscriptionRequestException(
                    "A linked user profile is required to subscribe to a travel");
        }
        return caller.userId();
    }

    // Le traveler annule son propre abonnement ; le manager proprietaire ou un Admin peut desabonner n'importe qui.
    private void requireCancellationRights(Travel travel, Subscription subscription, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        boolean isOwningManager =
                TRAVEL_MANAGER_ROLE.equals(caller.role()) && travel.getManagerId().equals(caller.userId());
        boolean isSubscriber = subscription.getTravelerId().equals(caller.userId());
        if (!isOwningManager && !isSubscriber) {
            throw new ForbiddenException("You can only cancel your own subscription");
        }
    }

    private void requireManagerOwnershipOrAdmin(Travel travel, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!travel.getManagerId().equals(caller.userId())) {
            throw new ForbiddenException("You can only view subscribers of your own travels");
        }
    }

    private boolean isPastCancellationCutoff(LocalDate travelStartDate) {
        return LocalDate.now(clock).isAfter(travelStartDate.minusDays(CANCELLATION_CUTOFF_DAYS));
    }

    private Travel getTravelOrThrow(UUID travelId) {
        return travelRepository.findById(travelId).orElseThrow(() -> new TravelNotFoundException(travelId));
    }
}
