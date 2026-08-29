package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Feedback;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    Optional<Feedback> findByTravel_IdAndTravelerId(UUID travelId, UUID travelerId);

    List<Feedback> findByTravel_Id(UUID travelId);

    // fix/audit-gaps (troubleshooting.md #40) : un Traveler ne pouvait jamais relire le contenu
    // de son propre feedback (GET .../feedbacks est reserve ADMIN/TRAVEL_MANAGER) - utilise par
    // TravelerStatsService.myFeedbacks, portee au caller uniquement (jamais un travelerId arbitraire).
    List<Feedback> findByTravelerId(UUID travelerId);

    // feat/manager-frontend : tous les avis recus sur l'ensemble des voyages d'un manager,
    // utilise pour calculer la note moyenne affichee sur sa page publique (ManagerStatsService).
    List<Feedback> findByTravel_ManagerId(UUID managerId);

    // feat/traveler-frontend : nombre d'avis LAISSES par le Traveler connecte (TravelerStatsService.myStats).
    long countByTravelerId(UUID travelerId);

    // Evite le N+1 d'AdminStatsService/ManagerStatsService (un findByTravel_Id par voyage) :
    // moyenne et nombre d'avis groupes en un seul aller-retour.
    @Query("SELECT f.travel.id AS travelId, AVG(f.rating) AS averageRating, COUNT(f) AS feedbackCount "
            + "FROM Feedback f WHERE f.travel.id IN :travelIds GROUP BY f.travel.id")
    List<TravelFeedbackAggregate> aggregateByTravelIds(@Param("travelIds") Collection<UUID> travelIds);
}
