package com.hs.notification.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Validates dashboard login credentials. The configured plaintext default
 * (hs-notification.security.admin-login.password) is hashed once at startup
 * via the same BCryptPasswordEncoder used for tenant API keys — it is never
 * stored or logged in plaintext beyond this constructor.
 */
@Service
public class AdminAuthService {

    private final String username;
    private final String passwordHash;
    private final AdminJwtService jwtService;

    public AdminAuthService(
            @Value("${hs-notification.security.admin-login.username}") String username,
            @Value("${hs-notification.security.admin-login.password}") String password,
            AdminJwtService jwtService) {
        this.username = username;
        this.passwordHash = ApiKeyResolver.BCRYPT.encode(password);
        this.jwtService = jwtService;
    }

    public Optional<String> login(String candidateUsername, String candidatePassword) {
        if (candidateUsername == null || candidatePassword == null) {
            return Optional.empty();
        }
        if (!username.equals(candidateUsername)) {
            return Optional.empty();
        }
        if (!ApiKeyResolver.BCRYPT.matches(candidatePassword, passwordHash)) {
            return Optional.empty();
        }
        return Optional.of(jwtService.issueToken(candidateUsername));
    }

    public long getTtlMinutes() {
        return jwtService.getTtlMinutes();
    }
}
