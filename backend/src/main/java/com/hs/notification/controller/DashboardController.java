package com.hs.notification.controller;

import com.hs.notification.dto.ChannelBreakdownResponse;
import com.hs.notification.dto.DashboardSummaryResponse;
import com.hs.notification.model.NotificationJob;
import com.hs.notification.model.Tenant;
import com.hs.notification.model.NotificationAuditLog;
import com.hs.notification.repository.NotificationAuditLogRepository;
import com.hs.notification.repository.NotificationJobRepository;
import com.hs.notification.repository.NotificationRuleRepository;
import com.hs.notification.repository.WatchdogStateRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final NotificationJobRepository jobRepository;
    private final NotificationRuleRepository ruleRepository;
    private final WatchdogStateRepository watchdogStateRepository;
    private final NotificationAuditLogRepository auditLogRepository;
    private final HealthEndpoint healthEndpoint;

    public DashboardController(NotificationJobRepository jobRepository,
                               NotificationRuleRepository ruleRepository,
                               WatchdogStateRepository watchdogStateRepository,
                               NotificationAuditLogRepository auditLogRepository,
                               HealthEndpoint healthEndpoint) {
        this.jobRepository = jobRepository;
        this.ruleRepository = ruleRepository;
        this.watchdogStateRepository = watchdogStateRepository;
        this.auditLogRepository = auditLogRepository;
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        OffsetDateTime since = OffsetDateTime.now().minusHours(24);

        long sent = jobRepository.countByTenant_TenantIdAndStatusAndCreatedAtAfter(
                tenant.getTenantId(), "SENT", since);
        long failed = jobRepository.countByTenant_TenantIdAndStatusAndCreatedAtAfter(
                tenant.getTenantId(), "FAILED", since);
        // Queue depth is a point-in-time snapshot, not a rolling window — a job stuck in
        // PENDING/RETRYING for more than 24h must still show up here, not silently drop off.
        long pending = jobRepository.countByTenant_TenantIdAndStatus(tenant.getTenantId(), "PENDING")
                + jobRepository.countByTenant_TenantIdAndStatus(tenant.getTenantId(), "RETRYING");

        long totalSentAllTime = jobRepository.countByTenant_TenantIdAndStatus(tenant.getTenantId(), "SENT");
        long totalFailedAllTime = jobRepository.countByTenant_TenantIdAndStatus(tenant.getTenantId(), "FAILED");

        long activeRules = ruleRepository.findByTenant_TenantId(tenant.getTenantId()).stream()
                .filter(r -> r.isActive() && "ACTIVE".equals(r.getStatus()))
                .count();

        boolean serviceUp = Status.UP.equals(healthEndpoint.health().getStatus());
        int consecutiveFailures = watchdogStateRepository.findFirstByOrderByWatchdogStateIdAsc()
                .map(s -> s.getConsecutiveFailures()).orElse(0);

        long totalLast24h = sent + failed;
        int healthScore = totalLast24h == 0
                ? (serviceUp ? 100 : 0)
                : (int) Math.round((sent * 100.0) / Math.max(1, totalLast24h));

        String serviceStatus = serviceUp ? "UP" : (consecutiveFailures > 0 ? "DOWN" : "UNKNOWN");

        double successRate = totalLast24h == 0 ? 100.0
                : Math.round((sent * 1000.0) / totalLast24h) / 10.0;

        List<NotificationAuditLog> successEvents = auditLogRepository
                .findByTenant_TenantIdAndEventTypeAndOccurredAtAfter(tenant.getTenantId(), "SEND_SUCCESS", since);
        long avgDeliveryMs = (long) successEvents.stream()
                .map(NotificationAuditLog::getResponseTimeMs)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().orElse(0);

        return ResponseEntity.ok(new DashboardSummaryResponse(
                sent, failed, pending, activeRules, healthScore, serviceStatus, successRate, avgDeliveryMs,
                totalSentAllTime, totalFailedAllTime));
    }

    private static final Map<String, String> CHANNEL_COLORS = Map.of(
            "EMAIL", "#6366f1",
            "WEBHOOK", "#f97316",
            "SLACK", "#10b981",
            "SMS", "#a855f7"
    );

    @GetMapping("/channel-breakdown")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ChannelBreakdownResponse>> channelBreakdown(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        OffsetDateTime since = OffsetDateTime.now().minusHours(24);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (NotificationJob job : jobRepository.findByTenant_TenantIdAndCreatedAtAfter(tenant.getTenantId(), since)) {
            String channel = (job.getRule() != null && job.getRule().getTemplate() != null)
                    ? job.getRule().getTemplate().getChannel()
                    : "EMAIL";
            counts.merge(channel, 1L, Long::sum);
        }

        List<ChannelBreakdownResponse> response = counts.entrySet().stream()
                .map(e -> new ChannelBreakdownResponse(e.getKey(), e.getValue(),
                        CHANNEL_COLORS.getOrDefault(e.getKey(), "#94a3b8")))
                .toList();
        return ResponseEntity.ok(response);
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) {
            throw new IllegalStateException("Tenant not resolved");
        }
        return tenant;
    }
}
