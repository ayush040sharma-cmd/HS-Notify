package com.hs.notification.service;

import com.hs.notification.model.NotificationAuditLog;
import com.hs.notification.model.NotificationJob;
import com.hs.notification.model.NotificationRule;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.NotificationAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuditService {

    private final NotificationAuditLogRepository auditLogRepository;

    public AuditService(NotificationAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(Tenant tenant, NotificationJob job, NotificationRule rule,
                     String eventType, String detail, String actor, Integer responseTimeMs) {
        NotificationAuditLog entry = new NotificationAuditLog();
        entry.setTenant(tenant);
        entry.setJob(job);
        entry.setRule(rule);
        entry.setEventType(eventType);
        entry.setEventDetail(detail);
        entry.setActor(actor);
        entry.setResponseTimeMs(responseTimeMs);
        entry.setOccurredAt(OffsetDateTime.now());
        auditLogRepository.save(entry);
    }

    public void log(Tenant tenant, NotificationJob job, NotificationRule rule, String eventType, String detail) {
        log(tenant, job, rule, eventType, detail, "system", null);
    }
}
