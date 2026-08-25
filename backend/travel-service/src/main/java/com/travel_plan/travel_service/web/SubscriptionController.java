package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.SubscriptionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travels/{travelId}/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@PathVariable UUID travelId, Authentication authentication) {
        return subscriptionService.subscribe(travelId, principal(authentication));
    }

    @DeleteMapping("/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @PathVariable UUID travelId, @PathVariable UUID subscriptionId, Authentication authentication) {
        subscriptionService.unsubscribe(travelId, subscriptionId, principal(authentication));
    }

    // Reserve au Travel Manager proprietaire du travel + Admin (verifie dans le service) :
    // un traveler ne doit pas voir la liste des autres abonnes d'un travel.
    @GetMapping
    public List<SubscriptionResponse> listSubscribers(@PathVariable UUID travelId, Authentication authentication) {
        return subscriptionService.listSubscribers(travelId, principal(authentication));
    }

    // feat/admin-dashboard-overview : ids des co-travelers, pour signaler "un autre traveler"
    // depuis TravelDetail - aucun changement SecurityConfig requis (voir SubscriptionService).
    @GetMapping("/co-travelers")
    public List<UUID> coTravelers(@PathVariable UUID travelId, Authentication authentication) {
        return subscriptionService.coTravelerIds(travelId, principal(authentication));
    }

    private AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
