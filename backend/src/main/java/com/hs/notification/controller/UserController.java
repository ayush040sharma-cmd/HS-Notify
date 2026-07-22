package com.hs.notification.controller;

import com.hs.notification.dto.ApiKeySummaryResponse;
import com.hs.notification.dto.UserResponse;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.ApiKeyRepository;
import com.hs.notification.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard-facing user & API-key listing, gated by the operator's JWT
 * session (unlike AdminController's key endpoints, which are gated by the
 * separate X-Admin-Token bootstrap credential). Scoped to the caller's own
 * tenant only — no cross-tenant lookup here.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AppUserRepository appUserRepository;
    private final ApiKeyRepository apiKeyRepository;

    public UserController(AppUserRepository appUserRepository, ApiKeyRepository apiKeyRepository) {
        this.appUserRepository = appUserRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        return ResponseEntity.ok(appUserRepository.findByTenant_TenantId(tenant.getTenantId())
                .stream().map(UserResponse::from).toList());
    }

    @GetMapping("/api-keys")
    public ResponseEntity<List<ApiKeySummaryResponse>> apiKeys(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        return ResponseEntity.ok(apiKeyRepository.findByTenant_TenantIdOrderByCreatedAtDesc(tenant.getTenantId())
                .stream().map(k -> ApiKeySummaryResponse.from(k, tenant.getTenantCode())).toList());
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
