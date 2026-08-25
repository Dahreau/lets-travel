package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.exception.InvalidTravelRequestException;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.SubscriptionResponse;
import com.travel_plan.travel_service.web.TravelerStatsResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// feat/traveler-frontend : tableau de bord personnel du Traveler connecte (docs/lets-travel_project.md).
// Aucune restriction de role (contrairement a ManagerStatsService.myStats) : un TRAVEL_MANAGER ou
// un ADMIN a un profil traveler herite acces aussi a ce tableau de bord, cf. le RoleHierarchy et
// l'exigence du sujet ("Travel Manager a acces a toutes les fonctionnalites Traveler").
@Service
@RequiredArgsConstructor
@Transactional
public class TravelerStatsService {

    private final SubscriptionRepository subscriptionRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReportRepository reportRepository;

    public TravelerStatsResponse myStats(AuthenticatedUser caller) {
        UUID travelerId = requireTravelerId(caller);

        long participationCount = subscriptionRepository.countByTravelerId(travelerId);
        long cancellationCount =
                subscriptionRepository.countByTravelerIdAndStatus(travelerId, SubscriptionStatus.CANCELLED);
        long feedbackCount = feedbackRepository.countByTravelerId(travelerId);
        long reportCount = reportRepository.countByReporterId(travelerId);

        return new TravelerStatsResponse(participationCount, feedbackCount, reportCount, cancellationCount);
    }

    public List<SubscriptionResponse> mySubscriptions(AuthenticatedUser caller) {
        UUID travelerId = requireTravelerId(caller);

        return subscriptionRepository.findByTravelerIdOrderBySubscribedAtDesc(travelerId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    // Un compte ADMIN par defaut n'a pas de fiche User (userId null), meme raisonnement que
    // SubscriptionService.requireTravelerId.
    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidTravelRequestException("A linked traveler profile is required for this dashboard");
        }
        return caller.userId();
    }
}
