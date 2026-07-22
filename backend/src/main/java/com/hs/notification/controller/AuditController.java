package com.hs.notification.controller;

import com.hs.notification.dto.NotificationAuditLogResponse;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.NotificationAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final NotificationAuditLogRepository auditLogRepository;

    public AuditController(NotificationAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<NotificationAuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {

        Tenant tenant = (Tenant) httpRequest.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");

        return ResponseEntity.ok(auditLogRepository.findByTenant_TenantIdOrderByOccurredAtDesc(
                tenant.getTenantId(), PageRequest.of(page, size)).map(NotificationAuditLogResponse::from));
    }

    @GetMapping("/job/{jobId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<NotificationAuditLogResponse>> forJob(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditLogRepository.findByJob_JobIdOrderByOccurredAtDesc(
                jobId, PageRequest.of(page, size)).map(NotificationAuditLogResponse::from));
    }
}
