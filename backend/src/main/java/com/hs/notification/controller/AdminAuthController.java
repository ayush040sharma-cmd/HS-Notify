package com.hs.notification.controller;

import com.hs.notification.security.AdminAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Dashboard login/logout for human operators. Distinct from tenant API keys
 * (ApiKeyAuthFilter) and the bootstrap admin-token (AdminController) — this
 * only gates the SPA itself. Excluded from AdminJwtAuthFilter and the tenant
 * ApiKeyAuthFilter so the login call itself doesn't require a prior session.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Optional<AdminAuthService.LoginResult> result = adminAuthService.login(request.username(), request.password());
        if (result.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
        return ResponseEntity.ok(Map.of(
                "token", result.get().token(),
                "username", request.username(),
                "displayName", result.get().displayName(),
                "role", result.get().role(),
                "expiresInMinutes", adminAuthService.getTtlMinutes()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Stateless JWT — logout is client-side (discard the token). Nothing to invalidate server-side.
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(String username, String password) {}
}
