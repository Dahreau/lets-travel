package com.travel_plan.payment_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// Verifie directement le comportement de la RoleHierarchy (sans contexte Spring complet) :
// meme mecanisme que travel-service (voir son SecurityConfigTest), copie ici pour rester
// coherent alors que payment-service a desormais lui aussi une RoleHierarchy a 3 niveaux.
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null);

    @Test
    void adminInheritsTravelManagerAndTravelerAuthorities() {
        Collection<? extends GrantedAuthority> reachable = securityConfig.roleHierarchy()
                .getReachableGrantedAuthorities(java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(reachable).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_TRAVEL_MANAGER", "ROLE_TRAVELER");
    }

    @Test
    void travelManagerInheritsTravelerButNotAdminAuthorities() {
        Collection<? extends GrantedAuthority> reachable = securityConfig.roleHierarchy()
                .getReachableGrantedAuthorities(java.util.List.of(new SimpleGrantedAuthority("ROLE_TRAVEL_MANAGER")));

        assertThat(reachable).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_TRAVEL_MANAGER", "ROLE_TRAVELER")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void travelerDoesNotInheritAnythingAbove() {
        Collection<? extends GrantedAuthority> reachable = securityConfig.roleHierarchy()
                .getReachableGrantedAuthorities(java.util.List.of(new SimpleGrantedAuthority("ROLE_TRAVELER")));

        assertThat(reachable).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TRAVELER");
    }
}
