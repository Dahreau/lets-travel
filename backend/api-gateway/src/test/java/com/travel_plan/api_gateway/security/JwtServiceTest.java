package com.travel_plan.api_gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.time.Instant;
import java.util.Date;
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
        String token = tokenSignedWith(KEY, "admin@travel-plan.com", "ADMIN", 3600);

        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo("admin@travel-plan.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-totally-different-secret-key-32bytes!".getBytes());
        String token = tokenSignedWith(otherKey, "admin@travel-plan.com", "ADMIN", 3600);

        assertThatThrownBy(() -> jwtService.validateAndParse(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = tokenSignedWith(KEY, "admin@travel-plan.com", "ADMIN", -3600);

        assertThatThrownBy(() -> jwtService.validateAndParse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    private String tokenSignedWith(SecretKey key, String subject, String role, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(key)
                .compact();
    }
}
