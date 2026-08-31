package com.travel_plan.user_service.bootstrap;

import com.travel_plan.user_service.domain.Role;
import com.travel_plan.user_service.domain.User;
import com.travel_plan.user_service.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

// fix/audit-gaps : doit rester identique a ADMIN_USER_ID dans auth-service.AdminSeeder.
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminProfileSeeder implements CommandLineRunner {

    private static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository userRepository;

    // Sans cette fiche, l'admin par defaut n'a pas de profil User (userId null cote auth-service
    // avant ce fix) et ne peut donc pas s'abonner/payer/laisser un avis comme un Traveler.
    @Override
    public void run(String... args) {
        if (userRepository.existsById(ADMIN_USER_ID)) {
            return;
        }

        User admin = User.builder()
                .id(ADMIN_USER_ID)
                .firstName("Admin")
                .lastName("Systeme")
                .email("admin@lets-travel.local")
                .role(Role.ADMIN)
                .build();

        // save() passe par merge() (id deja renseigne + generateur UUID) : une course entre 2
        // replicas leve ObjectOptimisticLockingFailureException ici, pas DataIntegrityViolationException.
        try {
            userRepository.save(admin);
            log.warn("Fiche profil de l'admin par defaut creee (id={}).", ADMIN_USER_ID);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
            log.info("Fiche profil de l'admin par defaut deja creee par un autre replica, rien a faire.");
        }
    }
}
