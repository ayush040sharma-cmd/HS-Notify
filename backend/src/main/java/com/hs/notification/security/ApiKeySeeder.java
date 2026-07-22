package com.hs.notification.security;

import com.hs.notification.model.ApiKey;
import com.hs.notification.repository.ApiKeyRepository;
import com.hs.notification.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On first startup, if the api_key table is empty for a tenant, seeds it
 * from the legacy hs-notification.security.api-keys property so deployments
 * upgrading from Milestone 1 don't lose access without any operator action.
 *
 * Format: "TENANTCODE:rawKey,TENANTCODE2:rawKey2"
 */
@Component
public class ApiKeySeeder {

    private static final Logger log = LoggerFactory.getLogger(ApiKeySeeder.class);

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final String legacyApiKeys;

    public ApiKeySeeder(ApiKeyRepository apiKeyRepository,
                        TenantRepository tenantRepository,
                        @Value("${hs-notification.security.api-keys:ZAIN:dev-local-key-change-me}") String legacyApiKeys) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
        this.legacyApiKeys = legacyApiKeys;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDevKeys() {
        if (legacyApiKeys == null || legacyApiKeys.isBlank()) return;

        for (String pair : legacyApiKeys.split(",")) {
            String[] parts = pair.trim().split(":", 2);
            if (parts.length != 2) continue;

            String tenantCode = parts[0].trim();
            String rawKey = parts[1].trim();

            tenantRepository.findByTenantCode(tenantCode).ifPresent(tenant -> {
                if (!apiKeyRepository.existsByTenant_TenantIdAndRevokedFalse(tenant.getTenantId())) {
                    ApiKey key = new ApiKey();
                    key.setTenant(tenant);
                    key.setKeyPrefix(rawKey.substring(0, Math.min(8, rawKey.length())));
                    key.setKeyHash(ApiKeyResolver.BCRYPT.encode(rawKey));
                    key.setDescription("Bootstrapped from legacy api-keys config");
                    key.setCreatedBy("system-bootstrap");
                    apiKeyRepository.save(key);
                    log.info("Seeded API key for tenant={} prefix={}****", tenantCode,
                            rawKey.substring(0, Math.min(4, rawKey.length())));
                }
            });
        }
    }
}
