package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Feedback;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    Optional<Feedback> findByTravel_IdAndTravelerId(UUID travelId, UUID travelerId);

    List<Feedback> findByTravel_Id(UUID travelId);

    // feat/manager-frontend : tous les avis recus sur l'ensemble des voyages d'un manager,
    // utilise pour calculer la note moyenne affichee sur sa page publique (ManagerStatsService).
    List<Feedback> findByTravel_ManagerId(UUID managerId);
}
