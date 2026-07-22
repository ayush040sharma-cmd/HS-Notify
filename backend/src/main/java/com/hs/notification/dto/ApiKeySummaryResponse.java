package com.hs.notification.dto;

import com.hs.notification.model.ApiKey;

import java.time.OffsetDateTime;

public record ApiKeySummaryResponse(
        Long keyId, String prefix, String label, String tenantCode, String status, OffsetDateTime createdAt
) {
    public static ApiKeySummaryResponse from(ApiKey k, String tenantCode) {
        String status = k.isRevoked() ? "REVOKED"
                : (k.getExpiresAt() != null && k.getExpiresAt().isBefore(OffsetDateTime.now())) ? "EXPIRED"
                : "ACTIVE";
        return new ApiKeySummaryResponse(
                k.getKeyId(), k.getKeyPrefix(),
                k.getDescription() != null ? k.getDescription() : k.getKeyPrefix(),
                tenantCode, status, k.getCreatedAt());
    }
}
