package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Travel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRepository extends JpaRepository<Travel, UUID> {

    // feat/manager-frontend : dashboard prive du manager (revenu estime, nombre de voyages) -
    // la liste complete (pas juste un count) est necessaire pour recalculer le revenu voyage par
    // voyage dans ManagerStatsService.
    List<Travel> findByManagerId(UUID managerId);

    // feat/manager-frontend : page publique manager - seul le compte est necessaire ici.
    long countByManagerId(UUID managerId);
}
