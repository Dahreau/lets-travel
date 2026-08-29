package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.exception.DuplicateFeedbackException;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidFeedbackRequestException;
import com.travel_plan.travel_service.exception.TravelNotFoundException;
import com.travel_plan.travel_service.graph.RecommendationSyncService;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.FeedbackRequest;
import com.travel_plan.travel_service.web.FeedbackResponse;
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
public class FeedbackService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String TRAVEL_MANAGER_ROLE = "TRAVEL_MANAGER";

    private final FeedbackRepository feedbackRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TravelRepository travelRepository;
    private final Clock clock;
    private final RecommendationSyncService recommendationSyncService;

    public FeedbackResponse submit(UUID travelId, FeedbackRequest request, AuthenticatedUser caller) {
        Travel travel = getTravelOrThrow(travelId);
        UUID travelerId = requireTravelerId(caller);

        if (!subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, travelerId)) {
            throw new ForbiddenException("Vous ne pouvez laisser un avis que sur un voyage auquel vous etiez abonne");
        }

        if (LocalDate.now(clock).isBefore(travel.getEndDate())) {
            throw new InvalidFeedbackRequestException("Un avis ne peut etre soumis qu'apres la fin du voyage");
        }

        feedbackRepository.findByTravel_IdAndTravelerId(travelId, travelerId).ifPresent(existing -> {
            throw new DuplicateFeedbackException("Avis deja soumis pour ce voyage");
        });

        Feedback feedback = Feedback.builder()
                .travel(travel)
                .travelerId(travelerId)
                .rating(request.rating())
                .comment(request.comment())
                .createdAt(Instant.now(clock))
                .build();

        Feedback saved = feedbackRepository.save(feedback);
        // feat/search-and-recommendations : une note est un signal plus fort qu'une simple
        // participation pour le moteur de recommandations (voir RecommendationRepository).
        recommendationSyncService.recordFeedback(travelerId, travelId, request.rating());
        return FeedbackResponse.from(saved);
    }

    // Reserve au Travel Manager proprietaire + Admin, pour le controle qualite - voir
    // docs/lets-travel_audit.md.
    public List<FeedbackResponse> listForTravel(UUID travelId, AuthenticatedUser caller) {
        Travel travel = getTravelOrThrow(travelId);
        requireManagerOwnershipOrAdmin(travel, caller);

        return feedbackRepository.findByTravel_Id(travelId).stream()
                .map(FeedbackResponse::from)
                .toList();
    }

    // Filet de securite : tout compte authentifie a desormais un profil lie (voir
    // user-service.AdminProfileSeeder pour l'admin par defaut).
    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidFeedbackRequestException("Un profil utilisateur lie est requis pour soumettre un avis");
        }
        return caller.userId();
    }

    private void requireManagerOwnershipOrAdmin(Travel travel, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!TRAVEL_MANAGER_ROLE.equals(caller.role()) || !travel.getManagerId().equals(caller.userId())) {
            throw new ForbiddenException("Vous ne pouvez consulter les avis que de vos propres voyages");
        }
    }

    private Travel getTravelOrThrow(UUID id) {
        return travelRepository.findById(id).orElseThrow(() -> new TravelNotFoundException(id));
    }
}
