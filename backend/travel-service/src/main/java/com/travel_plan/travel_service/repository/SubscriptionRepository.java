package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTravel_IdAndTravelerIdAndStatus(
            UUID travelId, UUID travelerId, SubscriptionStatus status);

    List<Subscription> findByTravel_Id(UUID travelId);

    // Preuve de participation (feedback/report, feat/traveler-experience) : n'importe quel
    // statut compte, y compris CANCELLED - avoir ete inscrit a un moment donne suffit,
    // contrairement au cutoff de desabonnement qui ne regarde que les abonnements ACTIVE.
    boolean existsByTravel_IdAndTravelerId(UUID travelId, UUID travelerId);
}
