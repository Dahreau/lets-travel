package com.travel_plan.auth_service.bootstrap;

import com.travel_plan.auth_service.domain.Admin;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.repository.AdminRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminSeeder implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultUsername;
    private final String defaultPassword;

    public AdminSeeder(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.default-username}") String defaultUsername,
            @Value("${app.admin.default-password}") String defaultPassword) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        if (adminRepository.count() > 0) {
            return;
        }

        Admin admin = Admin.builder()
                .username(defaultUsername)
                .passwordHash(passwordEncoder.encode(defaultPassword))
                .role(Role.ADMIN)
                .createdAt(Instant.now())
                .build();

        adminRepository.save(admin);
        log.warn("Admin par defaut '{}' cree avec le mot de passe de app.admin.default-password. Change-le des la premiere connexion.", defaultUsername);
    }
}
