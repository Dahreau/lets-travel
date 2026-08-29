package com.travel_plan.auth_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travel_plan.auth_service.exception.InvalidRegistrationTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        key = Keys.hmacShaKeyFor("test-secret-key-must-be-at-least-32-bytes-long!".getBytes());
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

    private String buildRegistrationToken(SecretKey signingKey, UUID userId, Instant expiration) {
        return Jwts.builder()
                .subject(userId == null ? "not-a-uuid" : userId.toString())
                .claim("purpose", "user-registration")
                .issuedAt(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void validatesRegistrationTokenAndReturnsUserId() {
        UUID userId = UUID.randomUUID();
        String token = buildRegistrationToken(key, userId, Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThat(jwtService.validateRegistrationToken(token)).isEqualTo(userId);
    }

    @Test
    void rejectsRegistrationTokenWithoutPurposeClaim() {
        String loginToken = jwtService.generateToken("traveler1", "TRAVELER", UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.validateRegistrationToken(loginToken))
                .isInstanceOf(InvalidRegistrationTokenException.class);
    }

    @Test
    void rejectsExpiredRegistrationToken() {
        String token = buildRegistrationToken(key, UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> jwtService.validateRegistrationToken(token))
                .isInstanceOf(InvalidRegistrationTokenException.class);
    }

    @Test
    void rejectsRegistrationTokenSignedWithDifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("another-totally-different-secret-key-32bytes!".getBytes());
        String token = buildRegistrationToken(otherKey, UUID.randomUUID(), Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> jwtService.validateRegistrationToken(token))
                .isInstanceOf(InvalidRegistrationTokenException.class);
    }

    @Test
    void rejectsRegistrationTokenWithNonUuidSubject() {
        String token = buildRegistrationToken(key, null, Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> jwtService.validateRegistrationToken(token))
                .isInstanceOf(InvalidRegistrationTokenException.class);
    }
}
