package com.hs.notification.model;

import jakarta.persistence.*;

@Entity
@Table(name = "attachment_rule")
public class AttachmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_rule_id")
    private Long attachmentRuleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(name = "attachment_source", nullable = false)
    private String attachmentSource;

    @Column(name = "report_identifier")
    private String reportIdentifier;

    @Column(name = "output_format", nullable = false)
    private String outputFormat = "PDF";

    @Column(name = "max_size_mb", nullable = false)
    private Integer maxSizeMb = 10;

    @Column(name = "on_generation_failure", nullable = false)
    private String onGenerationFailure = "SEND_WITHOUT_ATTACHMENT";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getAttachmentRuleId() { return attachmentRuleId; }
    public void setAttachmentRuleId(Long attachmentRuleId) { this.attachmentRuleId = attachmentRuleId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getAttachmentSource() { return attachmentSource; }
    public void setAttachmentSource(String attachmentSource) { this.attachmentSource = attachmentSource; }

    public String getReportIdentifier() { return reportIdentifier; }
    public void setReportIdentifier(String reportIdentifier) { this.reportIdentifier = reportIdentifier; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public Integer getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(Integer maxSizeMb) { this.maxSizeMb = maxSizeMb; }

    public String getOnGenerationFailure() { return onGenerationFailure; }
    public void setOnGenerationFailure(String onGenerationFailure) { this.onGenerationFailure = onGenerationFailure; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
