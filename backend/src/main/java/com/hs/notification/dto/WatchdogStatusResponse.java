package com.hs.notification.dto;

import java.time.OffsetDateTime;

public record WatchdogStatusResponse(
        int consecutiveFailures,
        int totalRestarts,
        int escalationsSent,
        int pollIntervalSeconds,
        int failThreshold,
        OffsetDateTime lastUpAt,
        OffsetDateTime lastDownAt,
        String currentStatus
) {}
