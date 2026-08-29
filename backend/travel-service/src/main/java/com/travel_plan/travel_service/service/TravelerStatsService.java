package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.exception.InvalidTravelRequestException;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.FeedbackResponse;
import com.travel_plan.travel_service.web.ReportResponse;
import com.travel_plan.travel_service.web.SubscriptionResponse;
import com.travel_plan.travel_service.web.TravelerStatsResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// feat/traveler-frontend : tableau de bord personnel du Traveler connecte. Pas de restriction de
// role : TRAVEL_MANAGER/ADMIN heritent aussi de cet acces via RoleHierarchy.
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

    // fix/audit-gaps (troubleshooting.md #40) : permet au Traveler de relire le contenu de son
    // feedback/signalements, pas seulement les comptes de myStats.
    public List<FeedbackResponse> myFeedbacks(AuthenticatedUser caller) {
        UUID travelerId = requireTravelerId(caller);

        return feedbackRepository.findByTravelerId(travelerId).stream()
                .map(FeedbackResponse::from)
                .toList();
    }

    public List<ReportResponse> myReports(AuthenticatedUser caller) {
        UUID travelerId = requireTravelerId(caller);

        return reportRepository.findByReporterId(travelerId).stream()
                .map(ReportResponse::from)
                .toList();
    }

    // Un compte ADMIN par defaut n'a pas de fiche User (userId null), meme raisonnement que
    // SubscriptionService.requireTravelerId.
    private UUID requireTravelerId(AuthenticatedUser caller) {
        if (caller.userId() == null) {
            throw new InvalidTravelRequestException("Un profil traveler lie est requis pour ce tableau de bord");
        }
        return caller.userId();
    }
}
