package com.hs.notification.security;

import com.hs.notification.model.AppUser;
import com.hs.notification.repository.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Validates dashboard login credentials against app_user.password_hash
 * (Phase 5 RBAC — replaces the single hardcoded admin-login.username/password
 * pair everyone shared; UserAuthSeeder grandfathers that pair into the
 * existing 'admin' row so it keeps working unchanged).
 */
@Service
public class AdminAuthService {

    private final AppUserRepository appUserRepository;
    private final AdminJwtService jwtService;

    public AdminAuthService(AppUserRepository appUserRepository, AdminJwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.jwtService = jwtService;
    }

    public Optional<LoginResult> login(String candidateUsername, String candidatePassword) {
        if (candidateUsername == null || candidatePassword == null) {
            return Optional.empty();
        }
        Optional<AppUser> userOpt = appUserRepository.findByUsername(candidateUsername);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = userOpt.get();
        if (!user.isActive() || user.getPasswordHash() == null) {
            return Optional.empty();
        }
        if (!ApiKeyResolver.BCRYPT.matches(candidatePassword, user.getPasswordHash())) {
            return Optional.empty();
        }
        String token = jwtService.issueToken(user.getUsername(), user.getRole());
        return Optional.of(new LoginResult(token, user.getRole(),
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()));
    }

    public long getTtlMinutes() {
        return jwtService.getTtlMinutes();
    }

    public record LoginResult(String token, String role, String displayName) {}
}
