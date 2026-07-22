package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_audit_log")
public class NotificationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private NotificationJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private NotificationRule rule;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_detail", columnDefinition = "TEXT")
    private String eventDetail;

    @Column(name = "actor")
    private String actor;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public NotificationJob getJob() { return job; }
    public void setJob(NotificationJob job) { this.job = job; }

    public NotificationRule getRule() { return rule; }
    public void setRule(NotificationRule rule) { this.rule = rule; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventDetail() { return eventDetail; }
    public void setEventDetail(String eventDetail) { this.eventDetail = eventDetail; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
