package com.travel_plan.auth_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void findsAccountByUsername() {
        Account account = Account.builder()
                .username("admin")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .createdAt(Instant.now())
                .build();
        accountRepository.save(account);

        assertThat(accountRepository.findByUsername("admin")).isPresent();
        assertThat(accountRepository.findByUsername("unknown")).isEmpty();
    }

    @Test
    void storesUserIdForNonAdminAccounts() {
        UUID userId = UUID.randomUUID();
        Account account = Account.builder()
                .username("manager1")
                .passwordHash("hashed")
                .role(Role.TRAVEL_MANAGER)
                .userId(userId)
                .createdAt(Instant.now())
                .build();
        accountRepository.save(account);

        assertThat(accountRepository.findByUsername("manager1")).isPresent()
                .get()
                .satisfies(found -> assertThat(found.getUserId()).isEqualTo(userId));
    }
}
