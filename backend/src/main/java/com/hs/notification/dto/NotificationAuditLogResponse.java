package com.hs.notification.dto;

import com.hs.notification.model.NotificationAuditLog;

import java.time.OffsetDateTime;

public record NotificationAuditLogResponse(
        Long auditId,
        OffsetDateTime occurredAt,
        String eventType,
        JobRef job,
        RuleRef rule,
        String eventDetail,
        String actor,
        Integer responseTimeMs
) {
    public record JobRef(Long jobId) {}
    public record RuleRef(String ruleCode) {}

    public static NotificationAuditLogResponse from(NotificationAuditLog log) {
        JobRef job = log.getJob() != null ? new JobRef(log.getJob().getJobId()) : null;
        RuleRef rule = log.getRule() != null ? new RuleRef(log.getRule().getRuleCode()) : null;
        return new NotificationAuditLogResponse(
                log.getAuditId(), log.getOccurredAt(), log.getEventType(),
                job, rule, log.getEventDetail(), log.getActor(), log.getResponseTimeMs());
    }
}
