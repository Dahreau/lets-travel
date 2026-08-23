package com.travel_plan.auth_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// feat/traveler-experience : AuthController.register() est le 1er appel a fixer un timestamp
// dans du "New Code" auth-service (login n'en cree pas) - voir troubleshooting.md #10, meme
// raisonnement/meme pattern que travel-service/config/ClockConfig.java. AccountController
// garde son Instant.now() nu (code pre-existant, non touche par cette branche, jamais remonte
// par Sonar puisque hors "New Code") - pas de raison de le toucher ici.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
