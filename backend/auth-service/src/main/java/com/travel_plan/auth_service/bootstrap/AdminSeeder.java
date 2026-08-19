package com.travel_plan.auth_service.bootstrap;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.repository.AccountRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminSeeder implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultUsername;
    private final String defaultPassword;

    public AdminSeeder(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.default-username}") String defaultUsername,
            @Value("${app.admin.default-password}") String defaultPassword) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            return;
        }

        Account admin = Account.builder()
                .username(defaultUsername)
                .passwordHash(passwordEncoder.encode(defaultPassword))
                .role(Role.ADMIN)
                .userId(null)
                .createdAt(Instant.now())
                .build();

        accountRepository.save(admin);
        log.warn("Admin par defaut '{}' cree avec le mot de passe de app.admin.default-password. Change-le des la premiere connexion.", defaultUsername);
    }
}
