package com.hs.notification.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_rule")
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(name = "trigger_event", nullable = false)
    private String triggerEvent;

    @Column(name = "trigger_source", nullable = false)
    private String triggerSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private NotificationTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_group_id")
    private RecipientGroup recipientGroup;

    /** STATIC_GROUP (default, existing behavior) | CURRENT_USER (resolve TO dynamically at send time). */
    @Column(name = "recipient_mode", nullable = false)
    private String recipientMode = "STATIC_GROUP";

    /** Only consulted when recipientMode=CURRENT_USER and no acting-user/case-owner identity resolves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fallback_recipient_group_id")
    private RecipientGroup fallbackRecipientGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_rule_id")
    private AttachmentRule attachmentRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalation_chain_id")
    private EscalationChain escalationChain;

    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount = 3;

    @Column(name = "retry_backoff_seconds", nullable = false)
    private Integer retryBackoffSeconds = 60;

    @Column(name = "retry_backoff_multiplier", nullable = false)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    private Double retryBackoffMultiplier = 2.0;

    @Column(name = "on_final_failure", nullable = false)
    private String onFinalFailure = "ESCALATE";

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }

    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }

    public NotificationTemplate getTemplate() { return template; }
    public void setTemplate(NotificationTemplate template) { this.template = template; }

    public RecipientGroup getRecipientGroup() { return recipientGroup; }
    public void setRecipientGroup(RecipientGroup recipientGroup) { this.recipientGroup = recipientGroup; }

    public String getRecipientMode() { return recipientMode; }
    public void setRecipientMode(String recipientMode) { this.recipientMode = recipientMode; }

    public RecipientGroup getFallbackRecipientGroup() { return fallbackRecipientGroup; }
    public void setFallbackRecipientGroup(RecipientGroup fallbackRecipientGroup) { this.fallbackRecipientGroup = fallbackRecipientGroup; }

    public AttachmentRule getAttachmentRule() { return attachmentRule; }
    public void setAttachmentRule(AttachmentRule attachmentRule) { this.attachmentRule = attachmentRule; }

    public EscalationChain getEscalationChain() { return escalationChain; }
    public void setEscalationChain(EscalationChain escalationChain) { this.escalationChain = escalationChain; }

    public Integer getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(Integer maxRetryCount) { this.maxRetryCount = maxRetryCount; }

    public Integer getRetryBackoffSeconds() { return retryBackoffSeconds; }
    public void setRetryBackoffSeconds(Integer retryBackoffSeconds) { this.retryBackoffSeconds = retryBackoffSeconds; }

    public Double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
    public void setRetryBackoffMultiplier(Double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }

    public String getOnFinalFailure() { return onFinalFailure; }
    public void setOnFinalFailure(String onFinalFailure) { this.onFinalFailure = onFinalFailure; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
