package com.travel_plan.user_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String USER_ID_CLAIM = "userId";

    // Distingue un jeton d'inscription d'un JWT de session normal.
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String REGISTRATION_PURPOSE = "user-registration";
    private static final long REGISTRATION_TOKEN_EXPIRATION_MINUTES = 10;

    private final SecretKey signingKey;

    public JwtService(SecretKey jwtSigningKey) {
        this.signingKey = jwtSigningKey;
    }

    public Claims validateAndParse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractRole(Claims claims) {
        return claims.get(ROLE_CLAIM, String.class);
    }

    // Null pour un compte ADMIN par defaut, qui n'a pas de fiche User associee.
    public UUID extractUserId(Claims claims) {
        String raw = claims.get(USER_ID_CLAIM, String.class);
        return raw == null ? null : UUID.fromString(raw);
    }

    // Meme cle partagee qu'auth-service : la signature prouve que ce userId vient de CE flux
    // d'inscription, jamais d'une valeur choisie par le client.
    public String generateRegistrationToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(PURPOSE_CLAIM, REGISTRATION_PURPOSE)
                .issuedAt(Date.from(now)) // NOSONAR java:S2143 - jjwt 0.12.x n'expose que des overloads java.util.Date, pas java.time.Instant
                .expiration(Date.from(now.plus(REGISTRATION_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES))) // NOSONAR java:S2143 - meme contrainte que ci-dessus
                .signWith(signingKey)
                .compact();
    }
}
