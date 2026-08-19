package com.travel_plan.auth_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        SecretKey key = Keys.hmacShaKeyFor("test-secret-key-must-be-at-least-32-bytes-long!".getBytes());
        jwtService = new JwtService(key, 60);
    }

    @Test
    void generatesAndValidatesToken() {
        String token = jwtService.generateToken("admin", "ADMIN");

        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(jwtService.extractRole(claims)).isEqualTo("ADMIN");
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-totally-different-secret-key-32bytes!".getBytes());
        JwtService otherService = new JwtService(otherKey, 60);
        String token = otherService.generateToken("admin", "ADMIN");

        assertThatThrownBy(() -> jwtService.validateAndParse(token))
                .isInstanceOf(SignatureException.class);
    }
}
