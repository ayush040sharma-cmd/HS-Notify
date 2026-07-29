package com.hs.notification.model;

import jakarta.persistence.*;

@Entity
@Table(name = "attachment_schema_provider")
public class AttachmentSchemaProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_schema_id", nullable = false)
    private AttachmentSchema attachmentSchema;

    /** Matches AttachmentProvider.key() — validated at the controller layer against the live registry, not a DB constraint. */
    @Column(name = "provider_key", nullable = false)
    private String providerKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AttachmentSchema getAttachmentSchema() { return attachmentSchema; }
    public void setAttachmentSchema(AttachmentSchema attachmentSchema) { this.attachmentSchema = attachmentSchema; }

    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
