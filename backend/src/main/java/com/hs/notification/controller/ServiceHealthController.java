package com.hs.notification.controller;

import com.hs.notification.dto.ServiceHealthItem;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Live dependency health for the dashboard's "System Status" panel and the
 * Watchdog page's "Dependency Health" panel — both call this same endpoint.
 */
@RestController
@RequestMapping("/api/v1/service-health")
public class ServiceHealthController {

    private final HealthEndpoint healthEndpoint;
    private final Scheduler scheduler;
    private final String reportServiceBaseUrl;

    public ServiceHealthController(HealthEndpoint healthEndpoint, Scheduler scheduler,
                                   @Value("${hs-notification.report-service.base-url:}") String reportServiceBaseUrl) {
        this.healthEndpoint = healthEndpoint;
        this.scheduler = scheduler;
        this.reportServiceBaseUrl = reportServiceBaseUrl;
    }

    @GetMapping
    public ResponseEntity<List<ServiceHealthItem>> list() {
        List<ServiceHealthItem> items = new ArrayList<>();
        items.add(fromHealthPath("Database (PostgreSQL)", "db"));
        items.add(fromHealthPath("Mail Server (SMTP)", "mail"));
        items.add(quartzHealth());
        items.add(reportServiceHealth());
        return ResponseEntity.ok(items);
    }

    private ServiceHealthItem fromHealthPath(String name, String path) {
        long start = System.currentTimeMillis();
        HealthComponent component = healthEndpoint.healthForPath(path);
        int responseTimeMs = (int) (System.currentTimeMillis() - start);

        if (component == null) {
            return new ServiceHealthItem(name, "UNKNOWN", null, "No health indicator registered");
        }
        String status = component.getStatus().getCode();
        String details = (component instanceof Health h && !h.getDetails().isEmpty())
                ? h.getDetails().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "))
                : null;
        return new ServiceHealthItem(name, status, responseTimeMs, details);
    }

    private ServiceHealthItem quartzHealth() {
        long start = System.currentTimeMillis();
        try {
            boolean up = scheduler.isStarted() && !scheduler.isShutdown();
            int jobCount = scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.anyGroup()).size();
            int responseTimeMs = (int) (System.currentTimeMillis() - start);
            return new ServiceHealthItem("Quartz Scheduler", up ? "UP" : "DOWN", responseTimeMs,
                    jobCount + " job(s) scheduled");
        } catch (SchedulerException e) {
            return new ServiceHealthItem("Quartz Scheduler", "DOWN", null, e.getMessage());
        }
    }

    private ServiceHealthItem reportServiceHealth() {
        if (reportServiceBaseUrl == null || reportServiceBaseUrl.isBlank()) {
            return new ServiceHealthItem("Report Service", "STUB", null,
                    "Not configured — set hs-notification.report-service.base-url");
        }
        return new ServiceHealthItem("Report Service", "UP", null, reportServiceBaseUrl);
    }
}
