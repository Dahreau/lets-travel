package com.travel_plan.payment_service.repository;

import com.travel_plan.payment_service.domain.PaymentMethod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
}
