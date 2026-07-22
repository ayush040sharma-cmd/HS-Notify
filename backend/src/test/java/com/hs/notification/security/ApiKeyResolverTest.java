package com.hs.notification.security;

import com.hs.notification.model.ApiKey;
import com.hs.notification.model.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the DB-backed ApiKeyResolver.
 * Uses the KeyLookup functional interface so no JpaRepository stub is needed —
 * avoids ByteBuddy/JDK-25 incompatibility.
 */
class ApiKeyResolverTest {

    private static final String RAW_KEY = "test-key-abcdef1234";
    private static final String BCRYPT_HASH = ApiKeyResolver.BCRYPT.encode(RAW_KEY);

    private Tenant zain;
    private ApiKey activeKey;

    @BeforeEach
    void setUp() {
        zain = new Tenant();
        zain.setTenantId(1L);
        zain.setTenantCode("ZAIN");
        zain.setActive(true);

        activeKey = new ApiKey();
        activeKey.setKeyId(1L);
        activeKey.setTenant(zain);
        activeKey.setKeyPrefix(RAW_KEY.substring(0, 8));
        activeKey.setKeyHash(BCRYPT_HASH);
        activeKey.setRevoked(false);
    }

    @Test
    void validKeyResolvesToTenantCode() {
        ApiKeyResolver resolver = resolverReturning(List.of(activeKey));
        assertThat(resolver.resolveTenantCode(RAW_KEY)).contains("ZAIN");
    }

    @Test
    void wrongKeyReturnsEmpty() {
        ApiKeyResolver resolver = resolverReturning(List.of(activeKey));
        assertThat(resolver.resolveTenantCode("wrong-key-xyz")).isEmpty();
    }

    @Test
    void nullKeyReturnsEmpty() {
        ApiKeyResolver resolver = resolverReturning(List.of());
        assertThat(resolver.resolveTenantCode(null)).isEmpty();
    }

    @Test
    void shortKeyReturnsEmpty() {
        ApiKeyResolver resolver = resolverReturning(List.of());
        assertThat(resolver.resolveTenantCode("short")).isEmpty();
    }

    @Test
    void expiredKeyReturnsEmpty() {
        activeKey.setExpiresAt(OffsetDateTime.now().minusDays(1));
        ApiKeyResolver resolver = resolverReturning(List.of(activeKey));
        assertThat(resolver.resolveTenantCode(RAW_KEY)).isEmpty();
    }

    @Test
    void futureExpiryIsAccepted() {
        activeKey.setExpiresAt(OffsetDateTime.now().plusDays(30));
        ApiKeyResolver resolver = resolverReturning(List.of(activeKey));
        assertThat(resolver.resolveTenantCode(RAW_KEY)).contains("ZAIN");
    }

    @Test
    void noCandidatesForPrefixReturnsEmpty() {
        ApiKeyResolver resolver = resolverReturning(List.of());
        assertThat(resolver.resolveTenantCode(RAW_KEY)).isEmpty();
    }

    // --- helper ---

    private ApiKeyResolver resolverReturning(List<ApiKey> candidates) {
        // KeyLookup is a @FunctionalInterface — lambda is all we need
        return new ApiKeyResolver(prefix -> candidates);
    }
}
