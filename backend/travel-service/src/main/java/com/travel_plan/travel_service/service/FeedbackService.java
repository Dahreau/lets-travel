package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.Travel;
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

    public FeedbackResponse submit(UUID travelId, FeedbackRequest request, AuthenticatedUser caller) {
        Travel travel = getTravelOrThrow(travelId);
        UUID travelerId = requireTravelerId(caller);

        if (!subscriptionRepository.existsByTravel_IdAndTravelerId(travelId, travelerId)) {
            throw new ForbiddenException("You can only leave feedback on a travel you were subscribed to");
        }

        if (LocalDate.now(clock).isBefore(travel.getEndDate())) {
            throw new InvalidFeedbackRequestException("Feedback can only be submitted after the travel has ended");
        }

        feedbackRepository.findByTravel_IdAndTravelerId(travelId, travelerId).ifPresent(existing -> {
            throw new DuplicateFeedbackException("Feedback already submitted for this travel");
        });

        Feedback feedback = Feedback.builder()
                .travel(travel)
                .travelerId(travelerId)
                .rating(request.rating())
                .comment(request.comment())
                .createdAt(Instant.now(clock))
                .build();

        return FeedbackResponse.from(feedbackRepository.save(feedback));
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

    // Un compte ADMIN par defaut n'a pas de fiche User (userId null) : il ne peut pas
    // etre "le" traveler d'un feedback, il n'y a rien a rattacher.
    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidFeedbackRequestException("A linked user profile is required to submit feedback");
        }
        return caller.userId();
    }

    private void requireManagerOwnershipOrAdmin(Travel travel, AuthenticatedUser caller) {
        if (ADMIN_ROLE.equals(caller.role())) {
            return;
        }
        if (!TRAVEL_MANAGER_ROLE.equals(caller.role()) || !travel.getManagerId().equals(caller.userId())) {
            throw new ForbiddenException("You can only view feedback for your own travels");
        }
    }

    private Travel getTravelOrThrow(UUID id) {
        return travelRepository.findById(id).orElseThrow(() -> new TravelNotFoundException(id));
    }
}
