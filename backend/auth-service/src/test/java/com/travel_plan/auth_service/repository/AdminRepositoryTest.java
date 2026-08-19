package com.travel_plan.auth_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.travel_plan.auth_service.domain.Admin;
import com.travel_plan.auth_service.domain.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminRepositoryTest {

    @Autowired
    private AdminRepository adminRepository;

    @Test
    void findsAdminByUsername() {
        Admin admin = Admin.builder()
                .username("admin")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .createdAt(Instant.now())
                .build();
        adminRepository.save(admin);

        assertThat(adminRepository.findByUsername("admin")).isPresent();
        assertThat(adminRepository.findByUsername("unknown")).isEmpty();
    }
}
