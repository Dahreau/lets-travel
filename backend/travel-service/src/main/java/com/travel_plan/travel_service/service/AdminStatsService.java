package com.travel_plan.travel_service.service;

import com.travel_plan.travel_service.domain.Feedback;
import com.travel_plan.travel_service.domain.ReportedType;
import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import com.travel_plan.travel_service.domain.Travel;
import com.travel_plan.travel_service.repository.FeedbackRepository;
import com.travel_plan.travel_service.repository.ReportRepository;
import com.travel_plan.travel_service.repository.SubscriptionRepository;
import com.travel_plan.travel_service.repository.TravelFeedbackAggregate;
import com.travel_plan.travel_service.repository.TravelRepository;
import com.travel_plan.travel_service.repository.TravelSubscriberCount;
import com.travel_plan.travel_service.web.AdminManagerRankingResponse;
import com.travel_plan.travel_service.web.AdminMonthlyRevenueResponse;
import com.travel_plan.travel_service.web.AdminTravelRankingResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Vue d'ensemble Admin (docs/lets-travel_project.md) : classement managers/voyages, meme
// patron que ManagerStatsService mais etendu a tous plutot qu'a "moi".
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private static final int REVENUE_MONTHS = 6;

    private final TravelRepository travelRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReportRepository reportRepository;
    private final Clock clock;

    public List<AdminManagerRankingResponse> managerRankings() {
        List<Travel> allTravels = travelRepository.findAll();
        Map<UUID, List<Travel>> travelsByManager =
                allTravels.stream().collect(Collectors.groupingBy(Travel::getManagerId));
        Map<UUID, Long> subscriberCountByTravelId =
                activeSubscriberCountsByTravelId(allTravels.stream().map(Travel::getId).toList());

        return travelsByManager.entrySet().stream()
                .map(entry -> toManagerRanking(entry.getKey(), entry.getValue(), subscriberCountByTravelId))
                .sorted(Comparator.comparingDouble(AdminManagerRankingResponse::performanceScore)
                        .reversed())
                .toList();
    }

    // 6 derniers mois glissants, abonnements ACTIVE uniquement - meme convention "estimee"
    // (prix x abonnes) que managerRankings/travelRankings, pas reconcilie avec payment-service.
    public List<AdminMonthlyRevenueResponse> monthlyRevenue() {
        YearMonth currentMonth = YearMonth.now(clock);
        Map<YearMonth, BigDecimal> revenueByMonth = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)
                .stream()
                .filter(subscription -> subscription.getTravel().getPrice() != null)
                .collect(Collectors.groupingBy(
                        this::subscribedMonth,
                        Collectors.reducing(
                                BigDecimal.ZERO, subscription -> subscription.getTravel().getPrice(), BigDecimal::add)));

        return IntStream.rangeClosed(0, REVENUE_MONTHS - 1)
                .mapToObj(i -> currentMonth.minusMonths((long) REVENUE_MONTHS - 1 - i))
                .map(month -> new AdminMonthlyRevenueResponse(
                        month.toString(), revenueByMonth.getOrDefault(month, BigDecimal.ZERO)))
                .toList();
    }

    private YearMonth subscribedMonth(Subscription subscription) {
        return YearMonth.from(subscription.getSubscribedAt().atZone(ZoneOffset.UTC));
    }

    public List<AdminTravelRankingResponse> travelRankings() {
        List<Travel> travels = travelRepository.findAll();
        List<UUID> travelIds = travels.stream().map(Travel::getId).toList();
        Map<UUID, Long> subscriberCountByTravelId = activeSubscriberCountsByTravelId(travelIds);
        Map<UUID, TravelFeedbackAggregate> feedbackAggregateByTravelId = feedbackAggregatesByTravelId(travelIds);

        return travels.stream()
                .map(travel -> toTravelRanking(travel, subscriberCountByTravelId, feedbackAggregateByTravelId))
                .sorted(Comparator.comparing(AdminTravelRankingResponse::revenue).reversed())
                .toList();
    }

    private AdminManagerRankingResponse toManagerRanking(
            UUID managerId, List<Travel> travels, Map<UUID, Long> subscriberCountByTravelId) {
        BigDecimal revenue = travels.stream()
                .filter(travel -> travel.getPrice() != null)
                .map(travel -> travel.getPrice()
                        .multiply(BigDecimal.valueOf(subscriberCountByTravelId.getOrDefault(travel.getId(), 0L))))
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

    private AdminTravelRankingResponse toTravelRanking(
            Travel travel, Map<UUID, Long> subscriberCountByTravelId, Map<UUID, TravelFeedbackAggregate> feedbackAggregateByTravelId) {
        long subscriberCount = subscriberCountByTravelId.getOrDefault(travel.getId(), 0L);
        BigDecimal revenue = travel.getPrice() != null
                ? travel.getPrice().multiply(BigDecimal.valueOf(subscriberCount))
                : BigDecimal.ZERO;
        TravelFeedbackAggregate aggregate = feedbackAggregateByTravelId.get(travel.getId());
        Double averageRating = aggregate != null ? aggregate.getAverageRating() : null;

        return new AdminTravelRankingResponse(
                travel.getId(), travel.getTitle(), travel.getManagerId(), subscriberCount, revenue, averageRating);
    }

    private Double averageRating(List<Feedback> feedbacks) {
        OptionalDouble average = feedbacks.stream().mapToInt(Feedback::getRating).average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    // Une requete groupee par voyage plutot qu'un countByTravel_IdAndStatus par iteration (N+1).
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
}
