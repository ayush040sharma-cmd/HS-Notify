package com.hs.notification.security;

import com.hs.notification.model.ApiKey;

import java.util.List;

/** Narrow seam used by ApiKeyResolver — testable without a full JPA stub. */
@FunctionalInterface
public interface KeyLookup {
    List<ApiKey> findByPrefixNotRevoked(String keyPrefix);
}
