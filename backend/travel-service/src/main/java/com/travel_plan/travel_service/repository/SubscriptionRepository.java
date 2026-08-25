package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTravel_IdAndTravelerIdAndStatus(
            UUID travelId, UUID travelerId, SubscriptionStatus status);

    List<Subscription> findByTravel_Id(UUID travelId);

    // Preuve de participation (feedback/report, feat/traveler-experience) : n'importe quel
    // statut compte, y compris CANCELLED - avoir ete inscrit a un moment donne suffit,
    // contrairement au cutoff de desabonnement qui ne regarde que les abonnements ACTIVE.
    boolean existsByTravel_IdAndTravelerId(UUID travelId, UUID travelerId);

    // feat/manager-frontend : nombre d'abonnes actifs d'UN voyage donne, utilise pour estimer
    // le revenu (prix x abonnes) dans ManagerStatsService.myStats.
    long countByTravel_IdAndStatus(UUID travelId, SubscriptionStatus status);

    // feat/manager-frontend : nombre de voyageurs DISTINCTS sur l'ensemble des voyages d'un
    // manager - un derived query name ne sait pas exprimer "count distinct sur une propriete",
    // d'ou le @Query explicite (seul cas du repository qui en a besoin).
    @Query("SELECT COUNT(DISTINCT s.travelerId) FROM Subscription s "
            + "WHERE s.travel.managerId = :managerId AND s.status = :status")
    long countDistinctTravelersByManagerIdAndStatus(
            @Param("managerId") UUID managerId, @Param("status") SubscriptionStatus status);

    // feat/traveler-frontend : historique personnel du Traveler connecte (TravelerStatsService).
    List<Subscription> findByTravelerIdOrderBySubscribedAtDesc(UUID travelerId);

    long countByTravelerId(UUID travelerId);

    long countByTravelerIdAndStatus(UUID travelerId, SubscriptionStatus status);

    // fix/audit-gaps : revenu mensuel Admin (AdminStatsService.monthlyRevenue), groupe ensuite
    // par mois de subscribedAt en memoire (pas de group-by-month portable en derived query).
    List<Subscription> findByStatus(SubscriptionStatus status);
}
