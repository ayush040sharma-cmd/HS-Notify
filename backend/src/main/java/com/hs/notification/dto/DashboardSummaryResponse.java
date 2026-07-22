package com.hs.notification.dto;

public record DashboardSummaryResponse(
        long emailsSent24h,
        long failedJobs24h,
        long pendingJobs,
        long activeRules,
        int healthScorePercent,
        String serviceStatus,
        double successRate,
        long avgDeliveryMs,
        long totalSentAllTime,
        long totalFailedAllTime
) {}
