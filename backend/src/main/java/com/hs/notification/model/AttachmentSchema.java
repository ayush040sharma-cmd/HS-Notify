package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "attachment_schema")
public class AttachmentSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    @Column(name = "created_on", nullable = false)
    private OffsetDateTime createdOn = OffsetDateTime.now();

    @OneToMany(mappedBy = "attachmentSchema", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttachmentSchemaProvider> providers;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedOn() { return createdOn; }
    public void setCreatedOn(OffsetDateTime createdOn) { this.createdOn = createdOn; }

    public List<AttachmentSchemaProvider> getProviders() { return providers; }
    public void setProviders(List<AttachmentSchemaProvider> providers) { this.providers = providers; }
}
