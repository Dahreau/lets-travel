package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.ManagerStatsService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travels/managers")
@RequiredArgsConstructor
public class ManagerStatsController {

    private final ManagerStatsService managerStatsService;

    // Tableau de bord prive : reserve au manager connecte lui-meme (verifie dans le service,
    // pas seulement via SecurityConfig) - voir ManagerStatsService.myStats.
    @GetMapping("/me/stats")
    public ManagerStatsResponse myStats(Authentication authentication) {
        return managerStatsService.myStats(principal(authentication));
    }

    // Page publique (voir docs/lets-travel_project.md, section Traveler) : ouverte a tout
    // utilisateur authentifie, pas seulement le manager concerne ou un admin - route couverte
    // par la regle generique GET /api/travels/** de SecurityConfig (TRAVELER minimum).
    @GetMapping("/{managerId}/public-stats")
    public ManagerPublicStatsResponse publicStats(@PathVariable UUID managerId) {
        return managerStatsService.publicStats(managerId);
    }

    private AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
