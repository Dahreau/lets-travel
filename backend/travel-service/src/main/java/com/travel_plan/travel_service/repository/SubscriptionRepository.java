package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Subscription;
import com.travel_plan.travel_service.domain.SubscriptionStatus;
import java.util.Collection;
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

    // fix/audit-gaps : verifie qu'un traveler a bien un abonnement (n'importe quel statut, meme
    // raison que existsByTravel_IdAndTravelerId ci-dessus) sur un des voyages d'un manager donne -
    // utilise par ManagerStatsService.isMySubscriber pour restreindre GET /api/users/{id} cote
    // user-service aux vrais abonnes du manager appelant (troubleshooting.md #38, IDOR).
    boolean existsByTravel_ManagerIdAndTravelerId(UUID managerId, UUID travelerId);

    // Evite le N+1 d'AdminStatsService/ManagerStatsService (une requete par voyage) : un seul
    // aller-retour groupe par voyage plutot qu'un countByTravel_IdAndStatus par iteration.
    @Query("SELECT s.travel.id AS travelId, COUNT(s) AS activeCount FROM Subscription s "
            + "WHERE s.travel.id IN :travelIds AND s.status = :status GROUP BY s.travel.id")
    List<TravelSubscriberCount> countActiveSubscribersGroupedByTravelIds(
            @Param("travelIds") Collection<UUID> travelIds, @Param("status") SubscriptionStatus status);
}
