package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.exception.AccountNotFoundException;
import com.travel_plan.auth_service.exception.ForbiddenException;
import com.travel_plan.auth_service.repository.AccountRepository;
import com.travel_plan.auth_service.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Cree les comptes de connexion (TRAVELER/TRAVEL_MANAGER/ADMIN). Admin-only :
// deja couvert par la regle par defaut de SecurityConfig (anyRequest().hasRole("ADMIN")),
// pas de reglage supplementaire necessaire. Pas d'inscription publique pour l'instant
// (non demandee par l'enonce) - a ouvrir plus tard si besoin.
@RestController
@RequestMapping("/api/auth/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = Account.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .userId(request.userId())
                .createdAt(Instant.now())
                .build();
        return AccountResponse.from(accountRepository.save(account));
    }

    // fix/audit-gaps (troubleshooting.md #41) : appele par user-service (client/AuthServiceClient)
    // dans deux flux - self-service (DELETE /api/users/me, l'appelant supprime SON PROPRE compte)
    // et admin (DELETE /api/users/{id}, un ADMIN supprime le compte de quelqu'un d'autre). Un seul
    // endpoint pour les deux, distingue par la garde ci-dessous : le JWT propage tel quel par
    // user-service porte l'identite du VRAI appelant, jamais falsifiable cote user-service.
    // Meme classe de garde que le fix IDOR #38 : le role seul (etre authentifie) ne suffit pas,
    // il faut soit ADMIN, soit etre proprietaire du userId cible.
    @DeleteMapping("/by-user/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByUserId(@PathVariable UUID userId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser caller)) {
            throw new ForbiddenException("Authentification requise");
        }
        boolean isAdmin = "ADMIN".equals(caller.role());
        boolean isSelf = userId.equals(caller.userId());
        if (!isAdmin && !isSelf) {
            throw new ForbiddenException("Non autorise a supprimer ce compte");
        }
        Account account =
                accountRepository.findByUserId(userId).orElseThrow(() -> new AccountNotFoundException(userId));
        accountRepository.delete(account);
    }
}
