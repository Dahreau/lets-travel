package com.travel_plan.travel_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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
                        // Un Travel Manager cree/modifie/supprime ses propres voyages (verifie en
                        // plus dans TravelService, la HttpSecurity ne sait pas encore lequel est "le sien").
                        .requestMatchers(HttpMethod.POST, "/api/travels").hasAnyRole("ADMIN", "TRAVEL_MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/travels/**").hasAnyRole("ADMIN", "TRAVEL_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/travels/**").hasAnyRole("ADMIN", "TRAVEL_MANAGER")
                        // Le reste (GET, y compris la liste complete) reste ADMIN-only pour l'instant :
                        // un "GET mes voyages" filtre pour un manager est une fonctionnalite a part
                        // (dashboard manager), pas encore construite.
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Detectee automatiquement par Spring Security (depuis 6.3) pour hasRole/hasAnyRole
    // en HTTP security ET en @PreAuthorize, sans handler a cabler a la main.
    // ADMIN herite de tout ce que peut faire TRAVEL_MANAGER, qui herite de tout ce que
    // peut faire TRAVELER - reflete la hierarchie decrite dans l'enonce Let's Travel.
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("TRAVEL_MANAGER")
                .role("TRAVEL_MANAGER").implies("TRAVELER")
                .build();
    }
}
