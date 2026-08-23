package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Report;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByTravel_Id(UUID travelId);

    List<Report> findAllByOrderByCreatedAtDesc();
}
