package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.exception.ForbiddenException;
import com.travel_plan.travel_service.exception.InvalidTravelRequestException;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.ManagerPublicStatsResponse;
import com.travel_plan.travel_service.web.ManagerStatsResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// feat/manager-frontend : stats du dashboard prive du manager + stats publiques consultees par
// un Traveler (docs/lets-travel_project.md). Volontairement sans nouvel appel inter-service :
// tout est calcule a partir de Travel/Subscription/Feedback/Report, deja dans travel-service -
// voir docs/nouveautes-vs-travel-plan.md pour le detail des simplifications assumees.
@Service
@RequiredArgsConstructor
@Transactional
public class ManagerStatsService {

    private static final String TRAVEL_MANAGER_ROLE = "TRAVEL_MANAGER";

    private final TravelRepository travelRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReportRepository reportRepository;

    // Un ADMIN n'a pas de "ses" voyages (contrairement a la liste d'abonnes/feedbacks d'UN
    // travel donne, ou l'admin a un droit de regard global) : ce dashboard est personnel au
    // manager connecte, donc on verifie explicitement le role plutot que de s'appuyer sur
    // hasRole/RoleHierarchy (qui laisserait un ADMIN passer, cf. TravelService.resolveManagerId
    // pour le meme raisonnement).
    public ManagerStatsResponse myStats(AuthenticatedUser caller) {
        if (!TRAVEL_MANAGER_ROLE.equals(caller.role())) {
            throw new ForbiddenException("Only a Travel Manager has a personal dashboard");
        }
        UUID managerId = caller.userId();
        if (managerId == null) {
            throw new InvalidTravelRequestException("A linked manager profile is required for this dashboard");
        }

        List<Travel> travels = travelRepository.findByManagerId(managerId);
        BigDecimal estimatedRevenue = travels.stream()
                .filter(travel -> travel.getPrice() != null)
                .map(travel -> travel.getPrice()
                        .multiply(BigDecimal.valueOf(activeSubscriberCount(travel.getId()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long travelerCount =
                subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerId, SubscriptionStatus.ACTIVE);

        return new ManagerStatsResponse(travels.size(), travelerCount, estimatedRevenue);
    }

    // Aucune validation que managerId correspond a un vrai manager : travel-service n'a pas de
    // reference locale vers les comptes (meme limite que Travel.managerId partout ailleurs) - un
    // id inconnu renvoie simplement des stats a zero plutot qu'un 404.
    public ManagerPublicStatsResponse publicStats(UUID managerId) {
        long travelCount = travelRepository.countByManagerId(managerId);

        List<Feedback> feedbacks = feedbackRepository.findByTravel_ManagerId(managerId);
        OptionalDouble average = feedbacks.stream().mapToInt(Feedback::getRating).average();
        Double averageRating = average.isPresent() ? average.getAsDouble() : null;

        long reportCount = reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerId);

        return new ManagerPublicStatsResponse(travelCount, averageRating, reportCount);
    }

    private long activeSubscriberCount(UUID travelId) {
        return subscriptionRepository.countByTravel_IdAndStatus(travelId, SubscriptionStatus.ACTIVE);
    }
}
