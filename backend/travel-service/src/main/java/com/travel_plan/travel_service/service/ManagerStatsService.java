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
import com.travel_plan.travel_service.repository.TravelFeedbackAggregate;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.repository.TravelSubscriberCount;
import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.web.ManagerPublicStatsResponse;
import com.travel_plan.travel_service.web.ManagerPublicTravelRatingEntry;
import com.travel_plan.travel_service.web.ManagerStatsResponse;
import com.travel_plan.travel_service.web.ManagerTravelStatsEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;
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
            throw new ForbiddenException("Seul un Travel Manager a un tableau de bord personnel");
        }
        UUID managerId = caller.userId();
        if (managerId == null) {
            throw new InvalidTravelRequestException("Un profil manager lie est requis pour ce tableau de bord");
        }

        List<Travel> travels = travelRepository.findByManagerId(managerId);
        List<UUID> travelIds = travels.stream().map(Travel::getId).toList();
        Map<UUID, Long> subscriberCountByTravelId = activeSubscriberCountsByTravelId(travelIds);
        Map<UUID, TravelFeedbackAggregate> feedbackAggregateByTravelId = feedbackAggregatesByTravelId(travelIds);

        BigDecimal estimatedRevenue = travels.stream()
                .filter(travel -> travel.getPrice() != null)
                .map(travel -> travel.getPrice()
                        .multiply(BigDecimal.valueOf(subscriberCountByTravelId.getOrDefault(travel.getId(), 0L))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long travelerCount =
                subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(managerId, SubscriptionStatus.ACTIVE);
        List<ManagerTravelStatsEntry> perTravel = travels.stream()
                .map(travel -> toTravelStatsEntry(travel, subscriberCountByTravelId, feedbackAggregateByTravelId))
                .toList();

        return new ManagerStatsResponse(travels.size(), travelerCount, estimatedRevenue, perTravel);
    }

    private ManagerTravelStatsEntry toTravelStatsEntry(
            Travel travel, Map<UUID, Long> subscriberCountByTravelId, Map<UUID, TravelFeedbackAggregate> feedbackAggregateByTravelId) {
        TravelFeedbackAggregate aggregate = feedbackAggregateByTravelId.get(travel.getId());
        Double averageRating = aggregate != null ? aggregate.getAverageRating() : null;
        long feedbackCount = aggregate != null ? aggregate.getFeedbackCount() : 0;
        return new ManagerTravelStatsEntry(
                travel.getId(), travel.getTitle(), subscriberCountByTravelId.getOrDefault(travel.getId(), 0L),
                averageRating, feedbackCount);
    }

    // Meme requete groupee qu'AdminStatsService (evite le N+1 par voyage) - dupliquee ici car
    // les deux services n'ont pas de base commune, portee volontairement restreinte a chacun.
    private Map<UUID, Long> activeSubscriberCountsByTravelId(List<UUID> travelIds) {
        if (travelIds.isEmpty()) {
            return Map.of();
        }
        return subscriptionRepository.countActiveSubscribersGroupedByTravelIds(travelIds, SubscriptionStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(TravelSubscriberCount::getTravelId, TravelSubscriberCount::getActiveCount));
    }

    private Map<UUID, TravelFeedbackAggregate> feedbackAggregatesByTravelId(List<UUID> travelIds) {
        if (travelIds.isEmpty()) {
            return Map.of();
        }
        return feedbackRepository.aggregateByTravelIds(travelIds).stream()
                .collect(Collectors.toMap(TravelFeedbackAggregate::getTravelId, aggregate -> aggregate));
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

        List<Travel> travels = travelRepository.findByManagerId(managerId);
        Map<UUID, TravelFeedbackAggregate> feedbackAggregateByTravelId =
                feedbackAggregatesByTravelId(travels.stream().map(Travel::getId).toList());
        List<ManagerPublicTravelRatingEntry> travelRatings = travels.stream()
                .map(travel -> toPublicTravelRating(travel, feedbackAggregateByTravelId))
                .toList();

        return new ManagerPublicStatsResponse(travelCount, averageRating, reportCount, travelRatings);
    }

    private ManagerPublicTravelRatingEntry toPublicTravelRating(
            Travel travel, Map<UUID, TravelFeedbackAggregate> feedbackAggregateByTravelId) {
        TravelFeedbackAggregate aggregate = feedbackAggregateByTravelId.get(travel.getId());
        Double averageRating = aggregate != null ? aggregate.getAverageRating() : null;
        long feedbackCount = aggregate != null ? aggregate.getFeedbackCount() : 0;
        return new ManagerPublicTravelRatingEntry(travel.getId(), travel.getTitle(), averageRating, feedbackCount);
    }

    // Consomme uniquement par user-service, jamais par le frontend directement.
    public boolean isMySubscriber(AuthenticatedUser caller, UUID travelerId) {
        if (!TRAVEL_MANAGER_ROLE.equals(caller.role())) {
            throw new ForbiddenException("Seul un Travel Manager peut verifier ses propres abonnes");
        }
        UUID managerId = caller.userId();
        if (managerId == null) {
            throw new InvalidTravelRequestException("Un profil manager lie est requis pour cette verification");
        }
        return subscriptionRepository.existsByTravel_ManagerIdAndTravelerId(managerId, travelerId);
    }
}
