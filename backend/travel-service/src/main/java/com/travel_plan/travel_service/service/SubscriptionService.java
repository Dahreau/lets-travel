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
                    throw new DuplicateSubscriptionException("Deja abonne a ce voyage");
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
                    "Les abonnements ne peuvent plus etre annules moins de "
                            + CANCELLATION_CUTOFF_DAYS + " jours avant le depart");
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

    // feat/admin-dashboard-overview : un Traveler ne peut signaler un autre Traveler que s'il a
    // lui-meme participe a ce voyage - meme regle que ReportService.requireConsistentTarget.
    public List<UUID> coTravelerIds(UUID travelId, AuthenticatedUser caller) {
        getTravelOrThrow(travelId);
        UUID travelerId = requireTravelerId(caller);
        if (!subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, travelerId)) {
            throw new ForbiddenException("Vous ne pouvez consulter les co-voyageurs que d'un voyage auquel vous avez participe");
        }

        return subscriptionRepository.findByTravel_Id(travelId).stream()
                .map(Subscription::getTravelerId)
                .filter(id -> !id.equals(travelerId))
                .distinct()
                .toList();
    }

    // Filet de securite : tout compte authentifie a desormais un profil lie (voir
    // user-service.AdminProfileSeeder pour l'admin par defaut).
    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidSubscriptionRequestException(
                    "Un profil utilisateur lie est requis pour s'abonner a un voyage");
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
            throw new ForbiddenException("Vous ne pouvez annuler que votre propre abonnement");
        }
    }

    private void requireManagerOwnershipOrAdmin(Travel travel, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!travel.getManagerId().equals(caller.userId())) {
            throw new ForbiddenException("Vous ne pouvez consulter les abonnes que de vos propres voyages");
        }
    }

    private boolean isPastCancellationCutoff(LocalDate travelStartDate) {
        return LocalDate.now(clock).isAfter(travelStartDate.minusDays(CANCELLATION_CUTOFF_DAYS));
    }

    private Travel getTravelOrThrow(UUID travelId) {
        return travelRepository.findById(travelId).orElseThrow(() -> new TravelNotFoundException(travelId));
    }
}
