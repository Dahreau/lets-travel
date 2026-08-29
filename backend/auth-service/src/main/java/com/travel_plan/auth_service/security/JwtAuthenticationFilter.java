package com.travel_plan.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.validateAndParse(token);
                String username = claims.getSubject();
                String role = jwtService.extractRole(claims);
                UUID userId = jwtService.extractUserId(claims);

                // fix/audit-gaps (troubleshooting.md #41) : principal enrichi (username+role+userId),
                // meme pattern que travel-service.AuthenticatedUser - necessaire pour que
                // AccountController.deleteByUserId puisse verifier "suis-je le proprietaire ?" sans
                // recevoir d'id falsifiable en parametre. userId peut etre null (compte ADMIN par
                // defaut sans fiche User) : dans ce cas isSelf sera toujours false, seul isAdmin
                // permettra l'acces - comportement voulu.
                var principal = new AuthenticatedUser(username, role, userId);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
