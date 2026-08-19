package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.repository.AccountRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
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
}
