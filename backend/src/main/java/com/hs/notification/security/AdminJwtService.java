package com.hs.notification.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates the short-lived JWT used for the human dashboard login
 * session. Entirely separate from tenant API keys (ApiKeyAuthFilter) and the
 * bootstrap admin-token (AdminController) — this only proves "a logged-in
 * operator is looking at the SPA," it carries no tenant-scoping meaning.
 */
@Component
public class AdminJwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public AdminJwtService(
            @Value("${hs-notification.security.admin-login.jwt-secret}") String secret,
            @Value("${hs-notification.security.admin-login.jwt-ttl-minutes:480}") long ttlMinutes) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "hs-notification.security.admin-login.jwt-secret must be at least 32 bytes (HMAC-SHA256 key)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.ttlMinutes = ttlMinutes;
    }

    public String issueToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** username + role of the session; empty if the token is missing, expired, or tampered with. */
    public Optional<SessionClaims> validateAndGetClaims(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String role = claims.get("role", String.class);
            return Optional.of(new SessionClaims(claims.getSubject(), role != null ? role : "VIEWER"));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record SessionClaims(String username, String role) {}

    public long getTtlMinutes() {
        return ttlMinutes;
    }
}
