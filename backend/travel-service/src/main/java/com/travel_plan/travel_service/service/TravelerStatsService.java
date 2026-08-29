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

    // fix/audit-gaps (troubleshooting.md #40) : jusqu'ici un Traveler ne pouvait relire ni le
    // contenu de son propre feedback ni celui de ses propres signalements (les GET dedies sont
    // reserves ADMIN/TRAVEL_MANAGER resp. ADMIN) - seuls les COMPTES (feedbackCount/reportCount
    // de myStats) lui etaient visibles. Meme garde-fou que mySubscriptions ci-dessus :
    // requireTravelerId force le caller.userId(), jamais un ID arbitraire.
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
