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

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String TRAVEL_MANAGER_ROLE = "TRAVEL_MANAGER";
    private static final String TRAVELER_ROLE = "TRAVELER";

    private final JwtService jwtService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // NOSONAR java:S4502 - API stateless (JWT en header Authorization, aucun cookie de session), donc pas de surface CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Routes /subscriptions AVANT les regles generiques /api/travels/** ci-dessous :
                        // sinon "DELETE /api/travels/**" (ADMIN/TRAVEL_MANAGER only) interceptait en
                        // premier un DELETE .../subscriptions/{id} et empechait un simple TRAVELER
                        // d'annuler son propre abonnement. S'abonner/se desabonner = TRAVELER minimum
                        // (herite par TRAVEL_MANAGER/ADMIN via la RoleHierarchy). Voir liste d'abonnes
                        // en revanche : reservee au Travel Manager proprietaire + Admin (verifie en
                        // plus dans SubscriptionService), pas visible par un simple traveler.
                        .requestMatchers(HttpMethod.POST, "/api/travels/*/subscriptions").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/travels/*/subscriptions/*").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/travels/*/subscriptions")
                        .hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // Un Travel Manager cree/modifie/supprime ses propres voyages (verifie en
                        // plus dans TravelService, la HttpSecurity ne sait pas encore lequel est "le sien").
                        .requestMatchers(HttpMethod.POST, "/api/travels").hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.PUT, "/api/travels/**").hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/travels/**").hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // Le reste (GET, y compris la liste complete) reste ADMIN-only pour l'instant :
                        // un "GET mes voyages" filtre pour un manager est une fonctionnalite a part
                        // (dashboard manager), pas encore construite.
                        .anyRequest().hasRole(ADMIN_ROLE))
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
                .role(ADMIN_ROLE).implies(TRAVEL_MANAGER_ROLE)
                .role(TRAVEL_MANAGER_ROLE).implies(TRAVELER_ROLE)
                .build();
    }
}
