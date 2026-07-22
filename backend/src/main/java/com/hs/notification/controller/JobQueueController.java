package com.hs.notification.controller;

import com.hs.notification.dto.NotificationJobResponse;
import com.hs.notification.dto.QueueStatsResponse;
import com.hs.notification.model.NotificationJob;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.NotificationJobRepository;
import com.hs.notification.service.AuditService;
import com.hs.notification.service.MailDispatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobQueueController {

    private final NotificationJobRepository jobRepository;
    private final MailDispatchService mailDispatchService;
    private final AuditService auditService;

    public JobQueueController(NotificationJobRepository jobRepository,
                              MailDispatchService mailDispatchService,
                              AuditService auditService) {
        this.jobRepository = jobRepository;
        this.mailDispatchService = mailDispatchService;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<NotificationJobResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            HttpServletRequest httpRequest) {

        Tenant tenant = resolveTenant(httpRequest);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<NotificationJob> jobs = (status != null && !status.isBlank())
                ? jobRepository.findByTenant_TenantIdAndStatusOrderByCreatedAtDesc(tenant.getTenantId(), status, pageable)
                : jobRepository.findByTenant_TenantIdOrderByCreatedAtDesc(tenant.getTenantId(), pageable);

        return ResponseEntity.ok(jobs.map(NotificationJobResponse::from));
    }

    @PostMapping("/{jobId}/requeue")
    @Transactional
    public ResponseEntity<NotificationJobResponse> requeue(@PathVariable Long jobId, HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        NotificationJob job = jobRepository.findById(jobId)
                .filter(j -> j.getTenant().getTenantId().equals(tenant.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        job.setStatus("PENDING");
        job.setNextRetryAt(null);
        job.setAttemptCount(0);
        jobRepository.save(job);

        String actor = httpRequest.getRemoteUser() != null ? httpRequest.getRemoteUser() : "operator";
        auditService.log(tenant, job, job.getRule(), "RETRY_SCHEDULED", "Manually requeued by operator", actor, null);

        mailDispatchService.attemptSend(job);
        return ResponseEntity.ok(NotificationJobResponse.from(job));
    }

    @PostMapping("/{jobId}/cancel")
    @Transactional
    public ResponseEntity<NotificationJobResponse> cancel(@PathVariable Long jobId, HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        NotificationJob job = jobRepository.findById(jobId)
                .filter(j -> j.getTenant().getTenantId().equals(tenant.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        job.setStatus("CANCELLED");
        job.setNextRetryAt(null);
        jobRepository.save(job);

        String actor = httpRequest.getRemoteUser() != null ? httpRequest.getRemoteUser() : "operator";
        auditService.log(tenant, job, job.getRule(), "SEND_FAILED", "Cancelled by operator", actor, null);

        return ResponseEntity.ok(NotificationJobResponse.from(job));
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<QueueStatsResponse> stats(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        Long tenantId = tenant.getTenantId();
        OffsetDateTime now = OffsetDateTime.now();

        long pending = jobRepository.countByTenant_TenantIdAndStatus(tenantId, "PENDING");
        long sending = jobRepository.countByTenant_TenantIdAndStatus(tenantId, "SENDING");
        long retrying = jobRepository.countByTenant_TenantIdAndStatus(tenantId, "RETRYING");
        long failed = jobRepository.countByTenant_TenantIdAndStatus(tenantId, "FAILED");
        long escalated = jobRepository.countByTenant_TenantIdAndStatus(tenantId, "ESCALATED");

        long oldestPendingAgeMinutes = jobRepository
                .findFirstByTenant_TenantIdAndStatusOrderByCreatedAtAsc(tenantId, "PENDING")
                .map(j -> Duration.between(j.getCreatedAt(), now).toMinutes())
                .orElse(0L);

        List<NotificationJob> recentlySent = jobRepository.findByTenant_TenantIdAndStatusAndSentAtAfter(
                tenantId, "SENT", now.minusHours(24));
        long avgProcessingTimeMs = recentlySent.isEmpty() ? 0 : (long) recentlySent.stream()
                .mapToLong(j -> Math.max(0, Duration.between(j.getCreatedAt(), j.getSentAt()).toMillis()))
                .average().orElse(0);

        long throughputPerHour = jobRepository.findByTenant_TenantIdAndStatusAndSentAtAfter(
                tenantId, "SENT", now.minusHours(1)).size();

        return ResponseEntity.ok(new QueueStatsResponse(
                pending, sending, retrying, failed, escalated,
                oldestPendingAgeMinutes, avgProcessingTimeMs, throughputPerHour));
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) {
            throw new IllegalStateException("Tenant not resolved");
        }
        return tenant;
    }
}
