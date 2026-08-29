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
                        // Inscription publique, 2e etape (identifiants + role=TRAVELER force) ; la 1ere
                        // etape (profil User) est dans user-service SecurityConfig.
                        .requestMatchers("/api/auth/register").permitAll()
                        // /me est appele par tous les roles juste apres login/register - sans cette regle
                        // il tombe dans anyRequest() (ADMIN uniquement).
                        .requestMatchers("/api/auth/me").authenticated()
                        // voir troubleshooting.md #41 - appele par user-service pour supprimer un compte lie ;
                        // la garde fine (ADMIN ou proprietaire) est dans AccountController.deleteByUserId.
                        .requestMatchers(HttpMethod.DELETE, "/api/auth/accounts/by-user/*").authenticated()
                        .anyRequest().hasRole("ADMIN"))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
