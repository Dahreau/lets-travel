package com.travel_plan.payment_service.repository;

import com.travel_plan.payment_service.domain.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
