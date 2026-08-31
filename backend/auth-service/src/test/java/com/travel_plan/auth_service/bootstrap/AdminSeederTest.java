package com.travel_plan.auth_service.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminSeederTest {

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private AdminSeeder seeder;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        seeder = new AdminSeeder(accountRepository, passwordEncoder, "admin", "changeme");
    }

    @Test
    void createsDefaultAdminWhenNoneExists() {
        when(accountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("changeme")).thenReturn("hashed");

        seeder.run();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        // fix/audit-gaps : l'admin par defaut doit avoir un profil lie pour pouvoir agir
        // comme un Traveler/Manager (voir user-service.AdminProfileSeeder, meme UUID fixe).
        assertThat(captor.getValue().getUserId()).isNotNull();
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() {
        when(accountRepository.count()).thenReturn(1L);

        seeder.run();

        verify(accountRepository, never()).save(any(Account.class));
    }

    // fix/audit-gaps : couvre le catch ajoute pour la course entre 2 replicas (voir troubleshooting.md #60).
    @Test
    void logsAndContinuesWhenAnotherReplicaAlreadyInsertedConcurrently() {
        when(accountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("changeme")).thenReturn("hashed");
        when(accountRepository.save(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate username"));

        seeder.run();

        verify(accountRepository).save(any(Account.class));
    }
}
