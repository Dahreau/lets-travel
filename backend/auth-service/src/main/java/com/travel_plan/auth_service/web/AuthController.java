package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.domain.Role;
import com.travel_plan.auth_service.exception.UsernameAlreadyTakenException;
import com.travel_plan.auth_service.repository.AccountRepository;
import com.travel_plan.auth_service.security.JwtService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
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
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtService.generateToken(account.getUsername(), account.getRole().name(), account.getUserId());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    // Public (voir SecurityConfig) : feat/traveler-experience, 2e etape de l'inscription
    // publique traveler (userId venant de POST /api/users/register cote user-service, appele
    // en 1er par le client). role toujours force a TRAVELER, jamais lu depuis la requete.
    // Connecte immediatement l'inscrit (meme reponse que /login) - evite un aller-retour de
    // plus pour l'UX, coherent avec "login process secure and straightforward" de l'audit.
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        accountRepository.findByUsername(request.username()).ifPresent(existing -> {
            throw new UsernameAlreadyTakenException("Username already taken: " + request.username());
        });

        Account account = Account.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.TRAVELER)
                .userId(request.userId())
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
