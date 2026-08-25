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
                        // feat/traveler-experience : meme raison que les routes /subscriptions
                        // ci-dessus, ces regles doivent precéder les regles generiques
                        // /api/travels/** plus bas (premier match qui gagne). Soumettre un
                        // feedback/signalement = TRAVELER minimum ; les consulter est reserve
                        // au Travel Manager proprietaire (feedback) ou a l'Admin seul (reports,
                        // moderation - un manager ne doit pas voir les signalements le concernant).
                        .requestMatchers(HttpMethod.POST, "/api/travels/*/feedbacks").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/travels/*/feedbacks")
                        .hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.POST, "/api/travels/*/reports").hasRole(TRAVELER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/reports").hasRole(ADMIN_ROLE)
                        // feat/manager-frontend : dashboard prive du manager - meme raison que les
                        // blocs ci-dessus (doit precéder la regle generique GET /api/travels/**, qui
                        // matcherait sinon ce chemin avec le role TRAVELER, trop permissif ici). La
                        // page publique manager (GET /{managerId}/public-stats) n'a pas besoin d'une
                        // regle dediee : elle est volontairement couverte par la regle generique
                        // ci-dessous (TRAVELER minimum), ouverte a tout utilisateur authentifie.
                        .requestMatchers(HttpMethod.GET, "/api/travels/managers/me/stats")
                        .hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // feat/admin-dashboard-overview : doit precéder GET /api/travels/** plus bas (TRAVELER
                        // minimum, trop permissif pour ce classement Admin-only).
                        .requestMatchers(HttpMethod.GET, "/api/travels/admin/**").hasRole(ADMIN_ROLE)
                        // Un Travel Manager cree/modifie/supprime ses propres voyages (verifie en
                        // plus dans TravelService, la HttpSecurity ne sait pas encore lequel est "le sien").
                        .requestMatchers(HttpMethod.POST, "/api/travels").hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.PUT, TRAVELS_WILDCARD).hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        .requestMatchers(HttpMethod.DELETE, TRAVELS_WILDCARD).hasAnyRole(ADMIN_ROLE, TRAVEL_MANAGER_ROLE)
                        // GET (liste + detail, + le sous-arbre /managers/** ci-dessus) ouvert a tout
                        // appelant authentifie (TRAVELER minimum, herite par TRAVEL_MANAGER/ADMIN) :
                        // un Traveler doit pouvoir consulter/parcourir les voyages pour s'y abonner
                        // (feat/traveler-subscriptions) et payer (feat/travel-pricing-and-traveler-payment,
                        // qui recupere le prix via ce meme GET, appele avec le JWT propage de
                        // l'appelant original - voir payment-service TravelServiceClient).
                        .requestMatchers(HttpMethod.GET, TRAVELS_WILDCARD).hasRole(TRAVELER_ROLE)
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
