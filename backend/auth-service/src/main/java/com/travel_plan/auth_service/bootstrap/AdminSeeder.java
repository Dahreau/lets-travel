package com.travel_plan.auth_service.bootstrap;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.repository.AccountRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminSeeder implements CommandLineRunner {

    // Doit rester identique a ADMIN_USER_ID dans user-service.AdminProfileSeeder.
    private static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

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
                .userId(ADMIN_USER_ID)
                .createdAt(Instant.now())
                .build();

        // 2 replicas peuvent passer le check count()>0 en parallele : l'un des deux plantait alors
        // sur la contrainte unique username (redemarrage en boucle) - voir troubleshooting.md #60.
        try {
            accountRepository.save(admin);
            log.warn("Admin par defaut '{}' cree avec le mot de passe de app.admin.default-password. Change-le des la premiere connexion.", defaultUsername);
        } catch (DataIntegrityViolationException exception) {
            log.info("Admin par defaut '{}' deja cree par un autre replica, rien a faire.", defaultUsername);
        }
    }
}
