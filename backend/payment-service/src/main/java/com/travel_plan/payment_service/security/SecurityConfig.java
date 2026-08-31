package com.travel_plan.payment_service.security;

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
                        // Un traveler consulte/paie SES paiements (verifie dans PaymentService). Matcher
                        // "/*" et pas "/**" : voir troubleshooting.md #63 (fuite de la liste complete sinon).
                        .requestMatchers(HttpMethod.GET, "/api/payments/*").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.POST, "/api/payments").hasRole(TRAVELER_ROLE)
                        // GET /api/payment-methods (liste) est filtree par proprietaire dans
                        // PaymentMethodService.findAll(), donc ouvrable au traveler sans risque de fuite.
                        .requestMatchers(HttpMethod.GET, "/api/payment-methods/**").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.POST, "/api/payment-methods").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.PUT, "/api/payment-methods/*").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/payment-methods/*").hasRole(TRAVELER_ROLE)
                        // Le reste (liste complete des paiements, remboursement, ...) reste ADMIN-only.
                        .anyRequest().hasRole(ADMIN_ROLE))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Meme mecanisme que travel-service : ADMIN implies TRAVEL_MANAGER implies TRAVELER, pour rester
    // coherent avec le modele de roles meme si aucune route ne differencie encore TRAVEL_MANAGER.
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN_ROLE).implies(TRAVEL_MANAGER_ROLE)
                .role(TRAVEL_MANAGER_ROLE).implies(TRAVELER_ROLE)
                .build();
    }
}
