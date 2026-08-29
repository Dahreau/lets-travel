package com.travel_plan.auth_service.repository;

import com.travel_plan.auth_service.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUsername(String username);

    // fix/audit-gaps (troubleshooting.md #41) : resout le compte de connexion a supprimer a
    // partir du userId (profil user-service), pas du username - AccountController.deleteByUserId
    // ne connait que le userId cible (self-service ou admin), jamais le username.
    Optional<Account> findByUserId(UUID userId);
}
