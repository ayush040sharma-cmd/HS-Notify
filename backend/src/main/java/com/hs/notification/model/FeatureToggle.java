package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "feature_toggle")
public class FeatureToggle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "toggle_id")
    private Long toggleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(name = "scope_type", nullable = false)
    private String scopeType = "TENANT";

    @Column(name = "scope_value")
    private String scopeValue;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getToggleId() { return toggleId; }
    public void setToggleId(Long toggleId) { this.toggleId = toggleId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getFeatureKey() { return featureKey; }
    public void setFeatureKey(String featureKey) { this.featureKey = featureKey; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getScopeValue() { return scopeValue; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
