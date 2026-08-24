package com.travel_plan.travel_service.graph;

import com.travel_plan.travel_service.domain.Destination;
import com.travel_plan.travel_service.domain.Travel;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// Graphe distinct de TravelGraphSyncService (Place/Route). Transaction Neo4j independante de
// celle de l'appelant : si elle echoue, la transaction JPA appelante est annulee (voir cette classe).
@Service
public class RecommendationSyncService {

    private static final BigDecimal BUDGET_MAX = new BigDecimal("500");
    private static final BigDecimal STANDARD_MAX = new BigDecimal("1500");
    private static final long SHORT_MAX_DAYS = 3;
    private static final long MEDIUM_MAX_DAYS = 7;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 10;

    private final RecommendationRepository recommendationRepository;
    private final TransactionTemplate neo4jTransactionTemplate;

    public RecommendationSyncService(
            RecommendationRepository recommendationRepository, Neo4jTransactionManager neo4jTransactionManager) {
        this.recommendationRepository = recommendationRepository;
        this.neo4jTransactionTemplate = new TransactionTemplate(neo4jTransactionManager);
    }

    public void upsertTravel(Travel travel) {
        String country = primaryCountry(travel);
        String priceRange = priceRange(travel.getPrice());
        String durationRange = durationRange(travel.getDurationDays());
        neo4jTransactionTemplate.executeWithoutResult(status ->
                recommendationRepository.upsertTravel(travel.getId().toString(), country, priceRange, durationRange));
    }

    public void deleteTravel(UUID travelId) {
        neo4jTransactionTemplate.executeWithoutResult(
                status -> recommendationRepository.deleteTravel(travelId.toString()));
    }

    public void recordParticipation(UUID travelerId, UUID travelId) {
        neo4jTransactionTemplate.executeWithoutResult(status ->
                recommendationRepository.recordParticipation(travelerId.toString(), travelId.toString()));
    }

    public void removeParticipation(UUID travelerId, UUID travelId) {
        neo4jTransactionTemplate.executeWithoutResult(status ->
                recommendationRepository.removeParticipation(travelerId.toString(), travelId.toString()));
    }

    public void recordFeedback(UUID travelerId, UUID travelId, int rating) {
        neo4jTransactionTemplate.executeWithoutResult(status ->
                recommendationRepository.recordFeedback(travelerId.toString(), travelId.toString(), rating));
    }

    // Spring Data garantit qu'une methode retournant une List ne renvoie jamais null.
    public List<UUID> recommend(UUID travelerId) {
        List<String> ids = neo4jTransactionTemplate.execute(status ->
                recommendationRepository.recommendTravelIds(travelerId.toString(), DEFAULT_RECOMMENDATION_LIMIT));
        return ids.stream().map(UUID::fromString).toList();
    }

    // Premiere destination par ordre : simplification assumee pour un voyage multi-destinations.
    private String primaryCountry(Travel travel) {
        return travel.getDestinations().stream()
                .min(Comparator.comparing(Destination::getOrderIndex))
                .map(Destination::getCountry)
                .orElse(null);
    }

    // Tranches volontairement simples et non converties entre devises (rush mode) - un vrai
    // systeme normaliserait le prix dans une devise commune avant de le comparer.
    private String priceRange(BigDecimal price) {
        if (price == null) {
            return null;
        }
        if (price.compareTo(BUDGET_MAX) < 0) {
            return "BUDGET";
        }
        if (price.compareTo(STANDARD_MAX) < 0) {
            return "STANDARD";
        }
        return "PREMIUM";
    }

    private String durationRange(long durationDays) {
        if (durationDays <= SHORT_MAX_DAYS) {
            return "SHORT";
        }
        if (durationDays <= MEDIUM_MAX_DAYS) {
            return "MEDIUM";
        }
        return "LONG";
    }
}
