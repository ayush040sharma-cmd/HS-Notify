package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Notification Action Registry row — replaces the hardcoded scenario set
 * that used to live as a Java constant in NotificationService. Deliberately
 * global (no tenant scoping), matching the exact behavior of the constant it
 * replaces. formSchemaId/attachmentSchemaId are plain nullable ids with no
 * JPA relationship yet — the tables they'll reference (form_schema,
 * attachment_schema) don't exist until later phases of this rollout.
 */
@Entity
@Table(name = "notification_action")
public class NotificationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = false;

    @Column(name = "default_channel", nullable = false)
    private String defaultChannel = "EMAIL";

    @Column(name = "icon")
    private String icon;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "role_required")
    private String roleRequired;

    @Column(name = "form_schema_id")
    private Long formSchemaId;

    @Column(name = "attachment_schema_id")
    private Long attachmentSchemaId;

    /**
     * True if this action is callable from HyperSense's Analyst Actions panel
     * (must degrade gracefully — flat fields only, no conditionals), as
     * opposed to an action reachable only from the internal dashboard
     * wizard. Defaults false so every pre-existing action keeps behaving as
     * it does today until explicitly opted in.
     */
    @Column(name = "hypersense_exposed", nullable = false)
    private boolean hypersenseExposed = false;

    /**
     * Names a ContextResolver bean invoked by GET /api/v1/actions/{code}/schema
     * ?caseId=X to merge resolved default values into the schema response
     * before HyperSense renders it. Null means no resolver — static schema
     * only, today's behavior.
     */
    @Column(name = "context_resolver_key")
    private String contextResolverKey;

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    @Column(name = "created_on", nullable = false)
    private OffsetDateTime createdOn = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(boolean approvalRequired) { this.approvalRequired = approvalRequired; }

    public String getDefaultChannel() { return defaultChannel; }
    public void setDefaultChannel(String defaultChannel) { this.defaultChannel = defaultChannel; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public String getRoleRequired() { return roleRequired; }
    public void setRoleRequired(String roleRequired) { this.roleRequired = roleRequired; }

    public Long getFormSchemaId() { return formSchemaId; }
    public void setFormSchemaId(Long formSchemaId) { this.formSchemaId = formSchemaId; }

    public Long getAttachmentSchemaId() { return attachmentSchemaId; }
    public void setAttachmentSchemaId(Long attachmentSchemaId) { this.attachmentSchemaId = attachmentSchemaId; }

    public boolean isHypersenseExposed() { return hypersenseExposed; }
    public void setHypersenseExposed(boolean hypersenseExposed) { this.hypersenseExposed = hypersenseExposed; }

    public String getContextResolverKey() { return contextResolverKey; }
    public void setContextResolverKey(String contextResolverKey) { this.contextResolverKey = contextResolverKey; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedOn() { return createdOn; }
    public void setCreatedOn(OffsetDateTime createdOn) { this.createdOn = createdOn; }
}
