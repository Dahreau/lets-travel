package com.travel_plan.travel_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Clock injectable partout, remplaçable par un Clock fixe dans les tests (satisfait aussi Sonar
// qui interdit un .now() nu). UTC explicite pour un "maintenant" independant du fuseau serveur.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
