package com.hs.notification.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "form_schema")
public class FormSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "created_by", nullable = false)
    private String createdBy = "system";

    @Column(name = "created_on", nullable = false)
    private OffsetDateTime createdOn = OffsetDateTime.now();

    @Column(name = "updated_on", nullable = false)
    private OffsetDateTime updatedOn = OffsetDateTime.now();

    @OneToMany(mappedBy = "formSchema", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FormField> fields;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedOn() { return createdOn; }
    public void setCreatedOn(OffsetDateTime createdOn) { this.createdOn = createdOn; }

    public OffsetDateTime getUpdatedOn() { return updatedOn; }
    public void setUpdatedOn(OffsetDateTime updatedOn) { this.updatedOn = updatedOn; }

    public List<FormField> getFields() { return fields; }
    public void setFields(List<FormField> fields) { this.fields = fields; }
}
