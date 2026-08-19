package com.travel_plan.auth_service.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.auth_service.domain.Admin;
import com.travel_plan.auth_service.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminSeederTest {

    private AdminRepository adminRepository;
    private PasswordEncoder passwordEncoder;
    private AdminSeeder seeder;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        seeder = new AdminSeeder(adminRepository, passwordEncoder, "admin", "changeme");
    }

    @Test
    void createsDefaultAdminWhenNoneExists() {
        when(adminRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("changeme")).thenReturn("hashed");

        seeder.run();

        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() {
        when(adminRepository.count()).thenReturn(1L);

        seeder.run();

        verify(adminRepository, never()).save(any(Admin.class));
    }
}
