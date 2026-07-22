package com.hs.notification.controller;

import com.hs.notification.dto.ApiKeySummaryResponse;
import com.hs.notification.model.ApiKey;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.ApiKeyRepository;
import com.hs.notification.repository.TenantRepository;
import com.hs.notification.security.ApiKeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only endpoints for API key lifecycle management.
 * Protected by X-Admin-Token (bootstrap credential, not a tenant API key).
 * These paths are excluded from the tenant ApiKeyAuthFilter.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final String adminToken;

    public AdminController(ApiKeyRepository apiKeyRepository,
                           TenantRepository tenantRepository,
                           @Value("${hs-notification.security.admin-token:changeme-admin-token}") String adminToken) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
        this.adminToken = adminToken;
    }

    @GetMapping("/api-keys/{tenantCode}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ApiKeySummaryResponse>> listKeys(
            @PathVariable String tenantCode,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        if (!adminToken.equals(token)) return ResponseEntity.status(401).build();

        Tenant tenant = tenantRepository.findByTenantCode(tenantCode)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantCode));

        return ResponseEntity.ok(apiKeyRepository.findByTenant_TenantIdOrderByCreatedAtDesc(tenant.getTenantId())
                .stream().map(k -> ApiKeySummaryResponse.from(k, tenant.getTenantCode())).toList());
    }

    @PostMapping("/api-keys")
    public ResponseEntity<Map<String, Object>> issueKey(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody IssueKeyRequest request) {

        if (!adminToken.equals(token)) return ResponseEntity.status(401).build();

        Tenant tenant = tenantRepository.findByTenantCode(request.tenantCode())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + request.tenantCode()));

        String rawKey = "hsnk-" + UUID.randomUUID().toString().replace("-", "");

        ApiKey key = new ApiKey();
        key.setTenant(tenant);
        key.setKeyPrefix(rawKey.substring(0, 8));
        key.setKeyHash(ApiKeyResolver.BCRYPT.encode(rawKey));
        key.setDescription(request.description());
        key.setCreatedBy(request.createdBy() != null ? request.createdBy() : "admin");
        if (request.expiresInDays() != null) {
            key.setExpiresAt(OffsetDateTime.now().plusDays(request.expiresInDays()));
        }
        apiKeyRepository.save(key);

        // Raw key returned ONCE — never stored in plaintext, log it masked.
        return ResponseEntity.ok(Map.of(
                "keyId", key.getKeyId(),
                "rawKey", rawKey,   // copy this now — it cannot be recovered later
                "keyPrefix", key.getKeyPrefix(),
                "tenantCode", request.tenantCode(),
                "expiresAt", key.getExpiresAt() != null ? key.getExpiresAt().toString() : "never",
                "warning", "Store this key securely — it will not be shown again."
        ));
    }

    @PostMapping("/api-keys/{keyId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeKey(
            @PathVariable Long keyId,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody(required = false) RevokeKeyRequest request) {

        if (!adminToken.equals(token)) return ResponseEntity.status(401).build();

        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

        key.setRevoked(true);
        key.setRevokedAt(OffsetDateTime.now());
        key.setRevokedBy(request != null && request.revokedBy() != null ? request.revokedBy() : "admin");
        apiKeyRepository.save(key);

        return ResponseEntity.ok(Map.of("keyId", keyId, "revoked", true, "revokedAt", key.getRevokedAt().toString()));
    }

    public record IssueKeyRequest(String tenantCode, String description, String createdBy, Integer expiresInDays) {}
    public record RevokeKeyRequest(String revokedBy) {}
}
