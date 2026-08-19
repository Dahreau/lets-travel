package com.travel_plan.auth_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.util.UUID;
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
    void generatesAndValidatesTokenWithUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken("manager1", "TRAVEL_MANAGER", userId);

        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo("manager1");
        assertThat(jwtService.extractRole(claims)).isEqualTo("TRAVEL_MANAGER");
        assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
    }

    @Test
    void generatesTokenWithoutUserIdForAdmin() {
        String token = jwtService.generateToken("admin", "ADMIN", null);

        Claims claims = jwtService.validateAndParse(token);

        assertThat(jwtService.extractRole(claims)).isEqualTo("ADMIN");
        assertThat(jwtService.extractUserId(claims)).isNull();
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-totally-different-secret-key-32bytes!".getBytes());
        JwtService otherService = new JwtService(otherKey, 60);
        String token = otherService.generateToken("admin", "ADMIN", null);

        assertThatThrownBy(() -> jwtService.validateAndParse(token))
                .isInstanceOf(SignatureException.class);
    }
}
