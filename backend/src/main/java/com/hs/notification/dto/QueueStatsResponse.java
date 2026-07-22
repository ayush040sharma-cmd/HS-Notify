package com.hs.notification.dto;

public record QueueStatsResponse(
        long pendingCount,
        long sendingCount,
        long retryingCount,
        long failedCount,
        long escalatedCount,
        long oldestPendingAgeMinutes,
        long avgProcessingTimeMs,
        long throughputPerHour
) {}
