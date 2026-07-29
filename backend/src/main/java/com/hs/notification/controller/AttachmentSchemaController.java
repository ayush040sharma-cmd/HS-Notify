package com.hs.notification.controller;

import com.hs.notification.dto.AttachmentSchemaResponse;
import com.hs.notification.dto.UpsertAttachmentSchemaRequest;
import com.hs.notification.model.AttachmentSchema;
import com.hs.notification.model.AttachmentSchemaProvider;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.AttachmentSchemaRepository;
import com.hs.notification.repository.NotificationActionRepository;
import com.hs.notification.service.AuditService;
import com.hs.notification.service.attachment.AttachmentProviderRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CRUD for attachment_schema — a named, ordered bundle of AttachmentProvider keys an action can offer. */
@RestController
@RequestMapping("/api/v1/attachment-schemas")
public class AttachmentSchemaController {

    private final AttachmentSchemaRepository attachmentSchemaRepository;
    private final NotificationActionRepository actionRepository;
    private final AttachmentProviderRegistry providerRegistry;
    private final AuditService auditService;

    public AttachmentSchemaController(AttachmentSchemaRepository attachmentSchemaRepository,
                                       NotificationActionRepository actionRepository,
                                       AttachmentProviderRegistry providerRegistry,
                                       AuditService auditService) {
        this.attachmentSchemaRepository = attachmentSchemaRepository;
        this.actionRepository = actionRepository;
        this.providerRegistry = providerRegistry;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AttachmentSchemaResponse>> list() {
        return ResponseEntity.ok(attachmentSchemaRepository.findAll()
                .stream().map(AttachmentSchemaResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<AttachmentSchemaResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(AttachmentSchemaResponse.from(requireSchema(id)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@Valid @RequestBody UpsertAttachmentSchemaRequest request, HttpServletRequest httpRequest) {
        String unknown = firstUnknownProviderKey(request.providerKeys());
        if (unknown != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown attachment provider key: " + unknown));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);

        AttachmentSchema schema = new AttachmentSchema();
        schema.setCreatedBy(actor);
        applyFields(schema, request);
        schema = attachmentSchemaRepository.save(schema);

        auditService.log(tenant, null, null, "ATTACHMENT_SCHEMA_CHANGED",
                "Attachment schema " + schema.getName() + " (id=" + schema.getId() + ") created", actor, null);
        return ResponseEntity.ok(AttachmentSchemaResponse.from(schema));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody UpsertAttachmentSchemaRequest request,
                                     HttpServletRequest httpRequest) {
        String unknown = firstUnknownProviderKey(request.providerKeys());
        if (unknown != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown attachment provider key: " + unknown));
        }

        AttachmentSchema schema = requireSchema(id);
        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);

        applyFields(schema, request);
        schema = attachmentSchemaRepository.save(schema);

        auditService.log(tenant, null, null, "ATTACHMENT_SCHEMA_CHANGED",
                "Attachment schema " + schema.getName() + " (id=" + id + ") edited", actor, null);
        return ResponseEntity.ok(AttachmentSchemaResponse.from(schema));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        AttachmentSchema schema = requireSchema(id);

        if (actionRepository.existsByAttachmentSchemaId(id)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Attachment schema is linked to one or more notification actions and cannot be deleted",
                    "attachmentSchemaId", id));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);
        String name = schema.getName();

        attachmentSchemaRepository.delete(schema);

        auditService.log(tenant, null, null, "ATTACHMENT_SCHEMA_CHANGED",
                "Attachment schema " + name + " (id=" + id + ") deleted", actor, null);
        return ResponseEntity.noContent().build();
    }

    private String firstUnknownProviderKey(List<String> providerKeys) {
        if (providerKeys == null) return null;
        return providerKeys.stream()
                .filter(key -> providerRegistry.find(key).isEmpty())
                .findFirst().orElse(null);
    }

    private void applyFields(AttachmentSchema schema, UpsertAttachmentSchemaRequest request) {
        schema.setName(request.name());
        schema.setDescription(request.description());

        if (schema.getProviders() == null) {
            schema.setProviders(new ArrayList<>());
        } else {
            schema.getProviders().clear();
        }
        if (request.providerKeys() == null) return;

        int order = 0;
        for (String key : request.providerKeys()) {
            AttachmentSchemaProvider p = new AttachmentSchemaProvider();
            p.setAttachmentSchema(schema);
            p.setProviderKey(key);
            p.setDisplayOrder(order++);
            schema.getProviders().add(p);
        }
    }

    private AttachmentSchema requireSchema(Long id) {
        return attachmentSchemaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Attachment schema not found: " + id));
    }

    private String actorOf(HttpServletRequest request) {
        return request.getRemoteUser() != null ? request.getRemoteUser() : "operator";
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
