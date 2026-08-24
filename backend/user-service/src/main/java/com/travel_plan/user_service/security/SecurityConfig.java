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
                        // feat/traveler-experience : inscription publique traveler, 1ere etape
                        // (creation du profil User, role force a TRAVELER cote UserService).
                        // Voir aussi auth-service SecurityConfig pour la 2e etape (identifiants).
                        .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                        // feat/manager-frontend : un Travel Manager doit pouvoir consulter le profil
                        // (nom, email...) d'un abonne a l'un de ses voyages, pour "view profiles" dans
                        // la gestion de sa liste d'abonnes (enonce, section Travel Manager). Il ne peut
                        // pas lister TOUS les users pour autant : GET /api/users (sans id) reste
                        // couvert par anyRequest ci-dessous, reserve a l'Admin.
                        .requestMatchers(HttpMethod.GET, "/api/users/*").hasAnyRole("ADMIN", "TRAVEL_MANAGER")
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
