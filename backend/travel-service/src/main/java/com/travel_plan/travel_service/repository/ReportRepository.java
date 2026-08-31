package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Report;
import com.travel_plan.travel_service.domain.ReportedType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByTravel_Id(UUID travelId);

    List<Report> findAllByOrderByCreatedAtDesc();

    // feat/manager-frontend : nombre de signalements contre ce manager (page publique) - uniquement
    // le compte, jamais le detail (reserve a l'Admin, voir SecurityConfig).
    long countByReportedTypeAndReportedId(ReportedType reportedType, UUID reportedId);

    // feat/traveler-frontend : signalements DEPOSES par le Traveler connecte (ne pas confondre avec
    // countByReportedTypeAndReportedId qui compte les signalements RECUS par un manager).
    long countByReporterId(UUID reporterId);

    // fix/audit-gaps (troubleshooting.md #40) : permet au Traveler de relire ses propres signalements,
    // scope au caller uniquement.
    List<Report> findByReporterId(UUID reporterId);
}
