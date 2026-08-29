package com.travel_plan.user_service.web;

import com.travel_plan.user_service.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    // voir troubleshooting.md #38 - callerIsAdmin/authorizationHeader restreignent ce endpoint aux
    // vrais abonnes ; authorizationHeader optionnel seulement pour les tests (toujours present en prod).
    @GetMapping("/{id}")
    public UserResponse findById(
            @PathVariable UUID id,
            Authentication authentication,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        boolean callerIsAdmin = authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return userService.findById(id, callerIsAdmin, authorizationHeader);
    }

    // authorizationHeader optionnel comme findById (tests) - toujours present en production
    // (SecurityConfig garantit un appelant ADMIN authentifie).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
            @Valid @RequestBody UserRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        return userService.create(request, authorizationHeader);
    }

    // Public (voir SecurityConfig), 1ere etape de l'inscription publique traveler.
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody UserRegistrationRequest request) {
        return userService.register(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        userService.delete(id, authorizationHeader);
    }

    // fix/audit-gaps (troubleshooting.md #41) : droit d'acces/portabilite RGPD - l'appelant
    // recupere SON PROPRE profil, sans jamais fournir d'id (voir UserService.me/requireUserId).
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.me(authentication);
    }

    // voir troubleshooting.md #41 - droit a l'effacement RGPD, supprime le profil et le compte de
    // connexion (UserService.deleteMe) ; authorizationHeader requis ici (pas optionnel comme findById).
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        userService.deleteMe(authentication, authorizationHeader);
    }
}
