package com.hs.notification.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "smtp_config")
public class SmtpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "smtp_config_id")
    private Long smtpConfigId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port = 587;

    @Column(name = "use_tls", nullable = false)
    private boolean useTls = true;

    @Column(name = "username")
    private String username;

    /** Pointer into the HS secrets vault. Plaintext passwords are never stored here. */
    @Column(name = "secret_ref", nullable = false)
    private String secretRef;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "from_display_name")
    private String fromDisplayName;

    @Column(name = "max_per_minute", nullable = false)
    private Integer maxPerMinute = 60;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Long getSmtpConfigId() { return smtpConfigId; }
    public void setSmtpConfigId(Long smtpConfigId) { this.smtpConfigId = smtpConfigId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public boolean isUseTls() { return useTls; }
    public void setUseTls(boolean useTls) { this.useTls = useTls; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getFromDisplayName() { return fromDisplayName; }
    public void setFromDisplayName(String fromDisplayName) { this.fromDisplayName = fromDisplayName; }

    public Integer getMaxPerMinute() { return maxPerMinute; }
    public void setMaxPerMinute(Integer maxPerMinute) { this.maxPerMinute = maxPerMinute; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
