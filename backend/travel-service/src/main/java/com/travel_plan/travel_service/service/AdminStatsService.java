package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.web.AdminManagerRankingResponse;
import com.travel_plan.travel_service.web.AdminTravelRankingResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Vue d'ensemble Admin (docs/lets-travel_project.md) : classement managers/voyages, meme
// patron que ManagerStatsService mais etendu a tous plutot qu'a "moi".
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private final TravelRepository travelRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReportRepository reportRepository;

    public List<AdminManagerRankingResponse> managerRankings() {
        Map<UUID, List<Travel>> travelsByManager =
                travelRepository.findAll().stream().collect(Collectors.groupingBy(Travel::getManagerId));

        return travelsByManager.entrySet().stream()
                .map(entry -> toManagerRanking(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(AdminManagerRankingResponse::performanceScore)
                        .reversed())
                .toList();
    }

    public List<AdminTravelRankingResponse> travelRankings() {
        return travelRepository.findAll().stream()
                .map(this::toTravelRanking)
                .sorted(Comparator.comparing(AdminTravelRankingResponse::revenue).reversed())
                .toList();
    }

    private AdminManagerRankingResponse toManagerRanking(UUID managerId, List<Travel> travels) {
        BigDecimal revenue = travels.stream()
                .filter(travel -> travel.getPrice() != null)
                .map(travel ->
                        travel.getPrice().multiply(BigDecimal.valueOf(activeSubscriberCount(travel.getId()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long travelerCount = subscriptionRepository.countDistinctTravelersByManagerIdAndStatus(
                managerId, SubscriptionStatus.ACTIVE);
        Double averageRating = averageRating(feedbackRepository.findByTravel_ManagerId(managerId));
        long reportCount = reportRepository.countByReportedTypeAndReportedId(ReportedType.MANAGER, managerId);

        // Score simple et explicable a l'oral (pas un modele "scientifique") : note moyenne x10 +
        // 1 pt / 100 [devise] de revenu - 5 pts par signalement recu.
        double score = (averageRating != null ? averageRating : 0) * 10 + revenue.doubleValue() / 100 - reportCount * 5;

        return new AdminManagerRankingResponse(
                managerId, travels.size(), travelerCount, revenue, averageRating, reportCount, score);
    }

    private AdminTravelRankingResponse toTravelRanking(Travel travel) {
        long subscriberCount = activeSubscriberCount(travel.getId());
        BigDecimal revenue = travel.getPrice() != null
                ? travel.getPrice().multiply(BigDecimal.valueOf(subscriberCount))
                : BigDecimal.ZERO;
        Double averageRating = averageRating(feedbackRepository.findByTravel_Id(travel.getId()));

        return new AdminTravelRankingResponse(
                travel.getId(), travel.getTitle(), travel.getManagerId(), subscriberCount, revenue, averageRating);
    }

    private Double averageRating(List<Feedback> feedbacks) {
        OptionalDouble average = feedbacks.stream().mapToInt(Feedback::getRating).average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private long activeSubscriberCount(UUID travelId) {
        return subscriptionRepository.countByTravel_IdAndStatus(travelId, SubscriptionStatus.ACTIVE);
    }
}
