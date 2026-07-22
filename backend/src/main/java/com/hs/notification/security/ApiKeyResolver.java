package com.hs.notification.security;

import com.hs.notification.model.ApiKey;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Resolves an inbound API key to a tenant code by looking up the DB-backed
 * api_key table (Milestone 2). Keys are BCrypt-hashed; lookup narrows
 * candidates by key_prefix before doing the hash comparison, so we never
 * do a full-table BCrypt scan.
 *
 * Depends on the narrow KeyLookup seam so unit tests can inject a lambda
 * instead of a full JpaRepository stub.
 */
@Component
public class ApiKeyResolver {

    public static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final KeyLookup keyLookup;

    public ApiKeyResolver(KeyLookup keyLookup) {
        this.keyLookup = keyLookup;
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveTenantCode(String rawKey) {
        if (rawKey == null || rawKey.length() < 8) return Optional.empty();

        String prefix = rawKey.substring(0, Math.min(8, rawKey.length()));
        List<ApiKey> candidates = keyLookup.findByPrefixNotRevoked(prefix);

        for (ApiKey candidate : candidates) {
            if (isExpired(candidate)) continue;
            if (BCRYPT.matches(rawKey, candidate.getKeyHash())) {
                return Optional.of(candidate.getTenant().getTenantCode());
            }
        }
        return Optional.empty();
    }

    private boolean isExpired(ApiKey key) {
        return key.getExpiresAt() != null && key.getExpiresAt().isBefore(OffsetDateTime.now());
    }
}
