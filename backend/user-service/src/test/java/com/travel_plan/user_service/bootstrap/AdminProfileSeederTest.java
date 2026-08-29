package com.travel_plan.user_service.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.user_service.domain.User;
import com.travel_plan.user_service.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class AdminProfileSeederTest {

    private static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private UserRepository userRepository;
    private AdminProfileSeeder seeder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        seeder = new AdminProfileSeeder(userRepository);
    }

    @Test
    void createsAdminProfileWhenNoneExists() {
        when(userRepository.existsById(ADMIN_USER_ID)).thenReturn(false);

        seeder.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(ADMIN_USER_ID);
    }

    @Test
    void doesNothingWhenAdminProfileAlreadyExists() {
        when(userRepository.existsById(ADMIN_USER_ID)).thenReturn(true);

        seeder.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void logsAndContinuesWhenAnotherReplicaAlreadyInsertedConcurrently() {
        when(userRepository.existsById(ADMIN_USER_ID)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        seeder.run();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void logsAndContinuesWhenAnotherReplicaWonTheMergeRaceConcurrently() {
        when(userRepository.existsById(ADMIN_USER_ID)).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(User.class, ADMIN_USER_ID));

        seeder.run();

        verify(userRepository).save(any(User.class));
    }
}
