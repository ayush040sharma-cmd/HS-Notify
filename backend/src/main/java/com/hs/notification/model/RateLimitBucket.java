package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rate_limit_bucket")
public class RateLimitBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bucket_id")
    private Long bucketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private NotificationRule rule;

    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart;

    @Column(name = "send_count", nullable = false)
    private Integer sendCount = 0;

    public Long getBucketId() { return bucketId; }
    public void setBucketId(Long bucketId) { this.bucketId = bucketId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public NotificationRule getRule() { return rule; }
    public void setRule(NotificationRule rule) { this.rule = rule; }

    public OffsetDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(OffsetDateTime windowStart) { this.windowStart = windowStart; }

    public Integer getSendCount() { return sendCount; }
    public void setSendCount(Integer sendCount) { this.sendCount = sendCount; }
}
