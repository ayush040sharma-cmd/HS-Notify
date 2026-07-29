package com.hs.notification.controller;

import com.hs.notification.dto.ApiKeySummaryResponse;
import com.hs.notification.dto.UpsertUserRequest;
import com.hs.notification.dto.UserResponse;
import com.hs.notification.model.AppUser;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.ApiKeyRepository;
import com.hs.notification.repository.AppUserRepository;
import com.hs.notification.security.ApiKeyResolver;
import com.hs.notification.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dashboard-facing user & API-key listing, gated by the operator's JWT
 * session (unlike AdminController's key endpoints, which are gated by the
 * separate X-Admin-Token bootstrap credential). Scoped to the caller's own
 * tenant only — no cross-tenant lookup here. Mutations (create/edit) are
 * ADMIN-only, enforced at the SecurityConfig path-matcher level.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "RAFM_HEAD", "MANAGER", "ANALYST", "VIEWER");

    private final AppUserRepository appUserRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditService auditService;

    public UserController(AppUserRepository appUserRepository, ApiKeyRepository apiKeyRepository,
                           AuditService auditService) {
        this.appUserRepository = appUserRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.auditService = auditService;
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

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@Valid @RequestBody UpsertUserRequest request, HttpServletRequest httpRequest) {
        if (!VALID_ROLES.contains(request.role())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown role: " + request.role(),
                    "validRoles", VALID_ROLES));
        }
        if (appUserRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "A user with username " + request.username() + " already exists"));
        }
        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "password is required to create a user"));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setActive(request.active() == null || request.active());
        user.setTenant(tenant);
        user.setPasswordHash(ApiKeyResolver.BCRYPT.encode(request.password()));
        user = appUserRepository.save(user);

        auditService.log(tenant, null, null, "USER_CHANGED",
                "User " + user.getUsername() + " (role=" + user.getRole() + ") created", actor, null);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody UpsertUserRequest request,
                                     HttpServletRequest httpRequest) {
        if (!VALID_ROLES.contains(request.role())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown role: " + request.role(),
                    "validRoles", VALID_ROLES));
        }
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        if (!user.getUsername().equals(request.username()) && appUserRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "A user with username " + request.username() + " already exists"));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);

        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setActive(request.active() == null || request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(ApiKeyResolver.BCRYPT.encode(request.password()));
        }
        user = appUserRepository.save(user);

        auditService.log(tenant, null, null, "USER_CHANGED",
                "User " + user.getUsername() + " (id=" + id + ") edited", actor, null);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    private String actorOf(HttpServletRequest request) {
        return request.getRemoteUser() != null ? request.getRemoteUser() : "operator";
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
