package com.travel_plan.travel_service.repository;

import com.travel_plan.travel_service.domain.Travel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRepository extends JpaRepository<Travel, UUID> {
}
