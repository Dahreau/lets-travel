package com.travel_plan.auth_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// voir troubleshooting.md #10 - AuthController.register() est le 1er appel a fixer un
// timestamp dans du "New Code" ici, meme pattern que travel-service/config/ClockConfig.java.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
