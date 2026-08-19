package com.travel_plan.auth_service.repository;

import com.travel_plan.auth_service.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUsername(String username);
}
