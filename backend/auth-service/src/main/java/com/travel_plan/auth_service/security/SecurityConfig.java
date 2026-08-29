package com.travel_plan.auth_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // NOSONAR java:S4502 - API stateless (JWT en header Authorization, aucun cookie de session), donc pas de surface CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        // feat/traveler-experience : inscription publique, 2e etape (identifiants +
                        // Account role=TRAVELER force). Voir user-service SecurityConfig pour la 1ere
                        // etape (profil User).
                        .requestMatchers("/api/auth/register").permitAll()
                        // /me est appele par tous les roles juste apres login/register (voir
                        // AuthController.me) - sans cette regle il tombe dans anyRequest() et
                        // seul ADMIN peut s'authentifier lui-meme.
                        .requestMatchers("/api/auth/me").authenticated()
                        // fix/audit-gaps (troubleshooting.md #41) : appele par user-service pour
                        // supprimer le compte de connexion associe a un profil supprime (self-service
                        // ou admin). Garde fine (ADMIN ou proprietaire) faite dans le controller,
                        // voir AccountController.deleteByUserId - ici juste "authentifie".
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/accounts/by-user/*").authenticated()
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
