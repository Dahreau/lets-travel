package com.travel_plan.user_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // NOSONAR java:S4502 - API stateless (JWT en header Authorization, aucun cookie de session), donc pas de surface CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // feat/traveler-experience : inscription publique traveler, 1ere etape (profil User,
                        // role force a TRAVELER). Voir aussi auth-service SecurityConfig pour la 2e etape.
                        .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                        // feat/manager-frontend : TRAVEL_MANAGER consulte le profil d'un abonne (pas tous les users).
                        // voir troubleshooting.md #41 - DOIT rester avant le matcher GET /api/users/* (ordre first-match).
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/*").hasAnyRole("ADMIN", "TRAVEL_MANAGER")
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
