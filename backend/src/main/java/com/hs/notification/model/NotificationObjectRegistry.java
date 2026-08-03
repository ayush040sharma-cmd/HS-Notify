package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Object Registry row (see V16__notification_object_registry migration) —
 * maps a NotifyRequest.sourceType value to where that object type's data
 * lives and which AttachmentProvider key can pull evidence for it.
 * object_type is a natural-key PK: it IS the sourceType value callers pass,
 * so no surrogate id is needed.
 */
@Entity
@Table(name = "notification_object_registry")
public class NotificationObjectRegistry {

    @Id
    @Column(name = "object_type")
    private String objectType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "source_table")
    private String sourceTable;

    @Column(name = "primary_key_column")
    private String primaryKeyColumn;

    @Column(name = "attachment_provider_key")
    private String attachmentProviderKey;

    @Column(name = "navigation_url_template")
    private String navigationUrlTemplate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    @Column(name = "created_on", nullable = false)
    private OffsetDateTime createdOn = OffsetDateTime.now();

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

    public String getPrimaryKeyColumn() { return primaryKeyColumn; }
    public void setPrimaryKeyColumn(String primaryKeyColumn) { this.primaryKeyColumn = primaryKeyColumn; }

    public String getAttachmentProviderKey() { return attachmentProviderKey; }
    public void setAttachmentProviderKey(String attachmentProviderKey) { this.attachmentProviderKey = attachmentProviderKey; }

    public String getNavigationUrlTemplate() { return navigationUrlTemplate; }
    public void setNavigationUrlTemplate(String navigationUrlTemplate) { this.navigationUrlTemplate = navigationUrlTemplate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedOn() { return createdOn; }
    public void setCreatedOn(OffsetDateTime createdOn) { this.createdOn = createdOn; }
}
