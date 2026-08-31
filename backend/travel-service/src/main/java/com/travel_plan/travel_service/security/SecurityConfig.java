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
    // Sonar S1192 (New Code) : ce literal apparaissait 3x (PUT/DELETE/GET) depuis l'ajout de
    // la regle GET par feat/travel-pricing-and-traveler-payment - voir troubleshooting.md #13.
    private static final String TRAVELS_WILDCARD = "/api/travels/**";

    private final JwtService jwtService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // NOSONAR java:S4502 - API stateless (JWT en header Authorization, aucun cookie de session), donc pas de surface CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Routes /subscriptions AVANT les regles generiques /api/travels/** - voir
                        // troubleshooting.md #61 (ordre des regles authorizeHttpRequests).
                        .requestMatchers(HttpMethod.POST, "/api/travels/*/subscriptions").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/travels/*/subscriptions/*").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/travels/*/subscriptions")
                        .hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // Meme raison que /subscriptions ci-dessus (troubleshooting.md #61) : doit
                        // precéder les regles generiques /api/travels/** plus bas.
                        .requestMatchers(HttpMethod.POST, "/api/travels/*/feedbacks").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/travels/*/feedbacks")
                        .hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.POST, "/api/travels/*/reports").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/reports").hasRole(ADMIN_ROLE)
                        // Dashboard prive manager - doit precéder GET /api/travels/** (TRAVELER,
                        // trop permissif ici). Page publique manager couverte par la regle generique.
                        .requestMatchers(HttpMethod.GET, "/api/travels/managers/me/stats")
                        .hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // fix/audit-gaps : endpoint interne consomme par user-service, meme raison
                        // que /me/stats ci-dessus (troubleshooting.md #38).
                        .requestMatchers(HttpMethod.GET, "/api/travels/managers/me/subscribers/*")
                        .hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // feat/admin-dashboard-overview : doit precéder GET /api/travels/** plus bas (TRAVELER
                        // minimum, trop permissif pour ce classement Admin-only).
                        .requestMatchers(HttpMethod.GET, "/api/travels/admin/**").hasRole(ADMIN_ROLE)
                        // Un Travel Manager cree/modifie/supprime ses propres voyages (verifie en
                        // plus dans TravelService, la HttpSecurity ne sait pas encore lequel est "le sien").
                        .requestMatchers(HttpMethod.POST, "/api/travels").hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.PUT, TRAVELS_WILDCARD).hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.DELETE, TRAVELS_WILDCARD).hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // GET (liste + detail) ouvert a tout appelant authentifie (TRAVELER minimum) :
                        // necessaire pour s'abonner et pour que payment-service recupere le prix via ce GET.
                        .requestMatchers(HttpMethod.GET, TRAVELS_WILDCARD).hasRole(TRAVELER_ROLE)
                        .anyRequest().hasRole(ADMIN_ROLE))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Detectee automatiquement par Spring Security (6.3+) pour hasRole/hasAnyRole. ADMIN herite
    // de TRAVEL_MANAGER, qui herite de TRAVELER (hierarchie de l'enonce Let's Travel).
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN_ROLE).implies(TRAVEL_MANAGER_ROLE)
                .role(TRAVEL_MANAGER_ROLE).implies(TRAVELER_ROLE)
                .build();
    }
}
