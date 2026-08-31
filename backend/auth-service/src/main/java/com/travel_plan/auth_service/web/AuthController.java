package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.exception.UsernameAlreadyTakenException;
import com.travel_plan.auth_service.repository.AccountRepository;
import com.travel_plan.auth_service.security.JwtService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Account account = accountRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BadCredentialsException("Identifiants invalides");
        }

        String token = jwtService.generateToken(account.getUsername(), account.getRole().name(), account.getUserId());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    // Public : 2e etape de l'inscription. Le userId vient du jeton signe par user-service,
    // jamais directement du client. Connecte immediatement l'inscrit (meme reponse que /login).
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = jwtService.validateRegistrationToken(request.registrationToken());

        accountRepository.findByUsername(request.username()).ifPresent(existing -> {
            throw new UsernameAlreadyTakenException("Nom d'utilisateur deja pris : " + request.username());
        });

        Account account = Account.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.TRAVELER)
                .userId(userId)
                .createdAt(Instant.now(clock))
                .build();
        accountRepository.save(account);

        String token = jwtService.generateToken(account.getUsername(), account.getRole().name(), account.getUserId());
        return new LoginResponse(token);
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .map(authority -> authority.replace("ROLE_", ""))
                .orElse("UNKNOWN");

        return ResponseEntity.ok(new MeResponse(authentication.getName(), role));
    }
}
