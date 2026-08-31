package com.travel_plan.auth_service.security;

import com.travel_plan.auth_service.exception.InvalidRegistrationTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String USER_ID_CLAIM = "userId";
    // Distingue un jeton d'inscription d'un JWT de session normal.
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String REGISTRATION_PURPOSE = "user-registration";

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(SecretKey jwtSigningKey, @Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = jwtSigningKey;
        this.expirationMinutes = expirationMinutes;
    }

    // userId : null pour un compte ADMIN par defaut, qui n'a pas de fiche User associee.
    public String generateToken(String username, String role, UUID userId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now)) // NOSONAR java:S2143 - jjwt 0.12.x n'expose que des overloads java.util.Date, pas java.time.Instant
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES))); // NOSONAR java:S2143 - meme contrainte que ci-dessus
        if (userId != null) {
            builder.claim(USER_ID_CLAIM, userId.toString());
        }
        return builder.signWith(signingKey).compact();
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

    public UUID extractUserId(Claims claims) {
        String raw = claims.get(USER_ID_CLAIM, String.class);
        return raw == null ? null : UUID.fromString(raw);
    }

    // Signe par user-service avec la meme cle partagee : la signature prouve que le userId
    // vient d'un POST /api/users/register reussi, jamais d'une valeur choisie par le client.
    public UUID validateRegistrationToken(String token) {
        Claims claims;
        try {
            claims = validateAndParse(token);
        } catch (JwtException e) {
            throw new InvalidRegistrationTokenException("Jeton d'inscription invalide ou expire");
        }
        if (!REGISTRATION_PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
            throw new InvalidRegistrationTokenException("Le jeton fourni n'est pas un jeton d'inscription valide");
        }
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException e) {
            throw new InvalidRegistrationTokenException("Jeton d'inscription invalide ou expire");
        }
    }
}
