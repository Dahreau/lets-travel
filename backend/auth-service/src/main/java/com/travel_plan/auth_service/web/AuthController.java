package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.domain.Account;
import com.travel_plan.auth_service.repository.AccountRepository;
import com.travel_plan.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
