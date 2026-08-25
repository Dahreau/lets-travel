package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Report;
import com.travel_plan.travel_service.domain.ReportedType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByTravel_Id(UUID travelId);

    List<Report> findAllByOrderByCreatedAtDesc();

    // feat/manager-frontend : nombre de signalements contre CE manager, affiche sur sa page
    // publique - uniquement le compte, jamais le detail (l'admin seul lit le contenu des
    // signalements, voir SecurityConfig et ReportService.listAll).
    long countByReportedTypeAndReportedId(ReportedType reportedType, UUID reportedId);

    // feat/traveler-frontend : nombre de signalements DEPOSES par le Traveler connecte
    // (TravelerStatsService.myStats), a ne pas confondre avec countByReportedTypeAndReportedId
    // ci-dessus qui compte les signalements RECUS par un manager.
    long countByReporterId(UUID reporterId);
}
