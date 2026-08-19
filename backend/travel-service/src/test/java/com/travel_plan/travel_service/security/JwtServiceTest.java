package com.travel_plan.travel_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor("test-secret-key-must-be-at-least-32-bytes-long!".getBytes());

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(KEY);
    }

    @Test
    void validatesAndParsesTokenSignedWithSameKey() {
        String token = tokenSignedWith(KEY, "admin@travel-plan.com", "ADMIN", null);

        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo("admin@travel-plan.com");
        assertThat(jwtService.extractRole(claims)).isEqualTo("ADMIN");
        assertThat(jwtService.extractUserId(claims)).isNull();
    }

    @Test
    void extractsUserIdWhenPresent() {
        UUID userId = UUID.randomUUID();
        String token = tokenSignedWith(KEY, "manager1", "TRAVEL_MANAGER", userId);

        Claims claims = jwtService.validateAndParse(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-totally-different-secret-key-32bytes!".getBytes());
        String token = tokenSignedWith(otherKey, "admin@travel-plan.com", "ADMIN", null);

        assertThatThrownBy(() -> jwtService.validateAndParse(token)).isInstanceOf(SignatureException.class);
    }

    private String tokenSignedWith(SecretKey key, String subject, String role, UUID userId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)));
        if (userId != null) {
            builder.claim("userId", userId.toString());
        }
        return builder.signWith(key).compact();
    }
}
