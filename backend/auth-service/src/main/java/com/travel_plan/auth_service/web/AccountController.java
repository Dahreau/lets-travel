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

// Cree les comptes de connexion (TRAVELER/TRAVEL_MANAGER/ADMIN). Admin-only via la regle
// par defaut de SecurityConfig - pas de reglage supplementaire necessaire.
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

    // voir troubleshooting.md #41 - endpoint unique pour self-service et admin, distingue par
    // la garde ci-dessous (meme classe de garde que le fix IDOR #38).
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
