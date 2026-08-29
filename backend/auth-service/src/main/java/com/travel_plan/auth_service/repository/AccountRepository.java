package com.travel_plan.auth_service.repository;

import com.travel_plan.auth_service.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUsername(String username);

    // voir troubleshooting.md #41 - resout le compte a supprimer via userId, pas username
    // (deleteByUserId ne connait que le userId cible).
    Optional<Account> findByUserId(UUID userId);
}
