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
}
