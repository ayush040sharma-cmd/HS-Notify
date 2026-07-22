package com.hs.notification.controller;

import com.hs.notification.dto.MetricsResponse;
import com.hs.notification.model.NotificationAuditLog;
import com.hs.notification.model.NotificationJob;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.NotificationAuditLogRepository;
import com.hs.notification.repository.NotificationJobRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final NotificationJobRepository jobRepository;
    private final NotificationAuditLogRepository auditLogRepository;

    public MetricsController(NotificationJobRepository jobRepository,
                             NotificationAuditLogRepository auditLogRepository) {
        this.jobRepository = jobRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<MetricsResponse> metrics(HttpServletRequest httpRequest) {
        Tenant tenant = (Tenant) httpRequest.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");

        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationJob> recentJobs = jobRepository.findByTenant_TenantIdAndCreatedAtAfter(
                tenant.getTenantId(), now.minusDays(7));

        long[] hourlySent = new long[24];
        long[] hourlyFailed = new long[24];
        long[] dailySent = new long[7];
        long[] dailyFailed = new long[7];

        for (NotificationJob job : recentJobs) {
            boolean success = "SENT".equals(job.getStatus());
            boolean failure = "FAILED".equals(job.getStatus()) || "ESCALATED".equals(job.getStatus());
            if (!success && !failure) continue;

            long hoursAgo = Duration.between(job.getCreatedAt(), now).toHours();
            if (hoursAgo >= 0 && hoursAgo < 24) {
                int idx = 23 - (int) hoursAgo;
                if (success) hourlySent[idx]++; else hourlyFailed[idx]++;
            }

            long daysAgo = Duration.between(job.getCreatedAt(), now).toDays();
            if (daysAgo >= 0 && daysAgo < 7) {
                int idx = 6 - (int) daysAgo;
                if (success) dailySent[idx]++; else dailyFailed[idx]++;
            }
        }

        List<MetricsResponse.HourlyPoint> hourly = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String label = String.format("%02d:00", now.minusHours(23 - i).getHour());
            hourly.add(new MetricsResponse.HourlyPoint(label, hourlySent[i], hourlyFailed[i]));
        }

        List<MetricsResponse.DailyPoint> daily = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String label = now.minusDays(6 - i).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            daily.add(new MetricsResponse.DailyPoint(label, dailySent[i], dailyFailed[i]));
        }

        List<Double> successRate = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            long total = dailySent[i] + dailyFailed[i];
            successRate.add(total == 0 ? 0.0 : Math.round((dailySent[i] * 1000.0) / total) / 10.0);
        }

        List<NotificationAuditLog> successEvents = auditLogRepository
                .findByTenant_TenantIdAndEventTypeAndOccurredAtAfter(tenant.getTenantId(), "SEND_SUCCESS", now.minusDays(7));
        long[] responseSum = new long[7];
        long[] responseCount = new long[7];
        for (NotificationAuditLog log : successEvents) {
            if (log.getResponseTimeMs() == null) continue;
            long daysAgo = Duration.between(log.getOccurredAt(), now).toDays();
            if (daysAgo >= 0 && daysAgo < 7) {
                int idx = 6 - (int) daysAgo;
                responseSum[idx] += log.getResponseTimeMs();
                responseCount[idx]++;
            }
        }
        List<Integer> avgResponseMs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            avgResponseMs.add(responseCount[i] == 0 ? 0 : (int) (responseSum[i] / responseCount[i]));
        }

        return ResponseEntity.ok(new MetricsResponse(hourly, daily, successRate, avgResponseMs));
    }
}
