package com.travel_plan.travel_service.web;

import com.travel_plan.travel_service.security.AuthenticatedUser;
import com.travel_plan.travel_service.service.TravelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travels")
@RequiredArgsConstructor
public class TravelController {

    private final TravelService travelService;

    @GetMapping
    public List<TravelResponse> findAll() {
        return travelService.findAll();
    }

    // "/search" placee avant "/{id}" par lisibilite ; Spring distingue les deux correctement quel que soit l'ordre.
    @GetMapping("/search")
    public List<TravelResponse> search(@RequestParam("q") String query) {
        return travelService.search(query);
    }

    @GetMapping("/autocomplete")
    public List<TravelResponse> autocomplete(@RequestParam("q") String query) {
        return travelService.autocomplete(query);
    }

    // Toujours "pour le Traveler connecte", pas d'id en parametre (voir RecommendationRepository).
    @GetMapping("/recommendations")
    public List<TravelResponse> recommendations(Authentication authentication) {
        return travelService.recommendations(principal(authentication));
    }

    @GetMapping("/{id}")
    public TravelResponse findById(@PathVariable UUID id) {
        return travelService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TravelResponse create(@Valid @RequestBody TravelRequest request, Authentication authentication) {
        return travelService.create(request, principal(authentication));
    }

    @PutMapping("/{id}")
    public TravelResponse update(
            @PathVariable UUID id, @Valid @RequestBody TravelRequest request, Authentication authentication) {
        return travelService.update(id, request, principal(authentication));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        travelService.delete(id, principal(authentication));
    }

    private AuthenticatedUser principal(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
