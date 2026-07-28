package com.hs.notification.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "notification_job")
public class NotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private NotificationRule rule;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "to_addresses", columnDefinition = "text[]")
    private List<String> toAddresses;

    @Column(name = "cc_addresses", columnDefinition = "text[]")
    private List<String> ccAddresses;

    @Column(name = "bcc_addresses", columnDefinition = "text[]")
    private List<String> bccAddresses;

    /** Only consulted for ruleless jobs — see MailDispatchService.resolveChannel. */
    @Column(name = "channel")
    private String channel;

    @Column(name = "subject")
    private String subject;

    @Column(name = "rendered_body", columnDefinition = "TEXT")
    private String renderedBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "jsonb")
    private Map<String, Object> contextJson;

    @Column(name = "attachment_status")
    private String attachmentStatus = "NOT_APPLICABLE";

    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount = 3;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "delivery_confirmed")
    private Boolean deliveryConfirmed;

    @Column(name = "bounce_reason", columnDefinition = "TEXT")
    private String bounceReason;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public NotificationRule getRule() { return rule; }
    public void setRule(NotificationRule rule) { this.rule = rule; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public List<String> getToAddresses() { return toAddresses; }
    public void setToAddresses(List<String> toAddresses) { this.toAddresses = toAddresses; }

    public List<String> getCcAddresses() { return ccAddresses; }
    public void setCcAddresses(List<String> ccAddresses) { this.ccAddresses = ccAddresses; }

    public List<String> getBccAddresses() { return bccAddresses; }
    public void setBccAddresses(List<String> bccAddresses) { this.bccAddresses = bccAddresses; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getRenderedBody() { return renderedBody; }
    public void setRenderedBody(String renderedBody) { this.renderedBody = renderedBody; }

    public Map<String, Object> getContextJson() { return contextJson; }
    public void setContextJson(Map<String, Object> contextJson) { this.contextJson = contextJson; }

    public String getAttachmentStatus() { return attachmentStatus; }
    public void setAttachmentStatus(String attachmentStatus) { this.attachmentStatus = attachmentStatus; }

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public Integer getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(Integer maxRetryCount) { this.maxRetryCount = maxRetryCount; }

    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Boolean getDeliveryConfirmed() { return deliveryConfirmed; }
    public void setDeliveryConfirmed(Boolean deliveryConfirmed) { this.deliveryConfirmed = deliveryConfirmed; }

    public String getBounceReason() { return bounceReason; }
    public void setBounceReason(String bounceReason) { this.bounceReason = bounceReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }
}
