package com.hs.notification.controller;

import com.hs.notification.dto.ActionSchemaResponse;
import com.hs.notification.dto.FormSchemaResponse;
import com.hs.notification.dto.NotificationActionResponse;
import com.hs.notification.dto.UpsertNotificationActionRequest;
import com.hs.notification.model.FormSchema;
import com.hs.notification.model.NotificationAction;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.FormSchemaRepository;
import com.hs.notification.repository.NotificationActionRepository;
import com.hs.notification.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CRUD for the Notification Action Registry — the database-driven
 * replacement for the hardcoded scenario set NotificationService used to
 * enforce (see NotificationService.submitCustomNotification). Deliberately
 * global (not tenant-scoped, see V7 migration comment); the tenant is still
 * resolved on writes purely to attribute audit-log entries, matching every
 * other config controller in this codebase.
 */
@RestController
@RequestMapping("/api/v1/actions")
public class NotificationActionController {

    private final NotificationActionRepository actionRepository;
    private final FormSchemaRepository formSchemaRepository;
    private final AuditService auditService;

    public NotificationActionController(NotificationActionRepository actionRepository,
                                         FormSchemaRepository formSchemaRepository,
                                         AuditService auditService) {
        this.actionRepository = actionRepository;
        this.formSchemaRepository = formSchemaRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationActionResponse>> list() {
        return ResponseEntity.ok(actionRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(NotificationActionResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<NotificationActionResponse> get(@PathVariable Long id) {
        NotificationAction action = requireAction(id);
        return ResponseEntity.ok(NotificationActionResponse.from(action));
    }

    /**
     * The read path the notification wizard actually calls: given an action
     * code, return its dynamic form definition. schema is null (200, not an
     * error) when the action has no form_schema_id linked — lets the
     * frontend fall back to a generic form for actions that predate the
     * Form Metadata Engine, like the 5 legacy scenarios other than
     * FRAUD_ALERT.
     */
    @GetMapping("/{code}/schema")
    @Transactional(readOnly = true)
    public ResponseEntity<ActionSchemaResponse> schemaForAction(@PathVariable String code) {
        NotificationAction action = actionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Notification action not found: " + code));

        FormSchemaResponse schema = Optional.ofNullable(action.getFormSchemaId())
                .flatMap(formSchemaRepository::findById)
                .map(FormSchemaResponse::from)
                .orElse(null);

        return ResponseEntity.ok(new ActionSchemaResponse(action.getCode(), schema));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@Valid @RequestBody UpsertNotificationActionRequest request,
                                     HttpServletRequest httpRequest) {
        if (actionRepository.existsByCode(request.code())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "An action with code " + request.code() + " already exists",
                    "code", request.code()));
        }

        NotificationAction action = new NotificationAction();
        applyFields(action, request);
        String exposureError = validateHypersenseExposure(action);
        if (exposureError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", exposureError));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);
        action.setCreatedBy(actor);
        action = actionRepository.save(action);

        auditService.log(tenant, null, null, "ACTION_CHANGED",
                "Action " + action.getCode() + " created", actor, null);
        return ResponseEntity.ok(NotificationActionResponse.from(action));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long id,
                                     @Valid @RequestBody UpsertNotificationActionRequest request,
                                     HttpServletRequest httpRequest) {
        NotificationAction action = requireAction(id);

        if (!action.getCode().equals(request.code()) && actionRepository.existsByCode(request.code())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "An action with code " + request.code() + " already exists",
                    "code", request.code()));
        }

        applyFields(action, request);
        String exposureError = validateHypersenseExposure(action);
        if (exposureError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", exposureError));
        }

        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);
        action = actionRepository.save(action);

        auditService.log(tenant, null, null, "ACTION_CHANGED",
                "Action " + action.getCode() + " edited", actor, null);
        return ResponseEntity.ok(NotificationActionResponse.from(action));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        NotificationAction action = requireAction(id);
        Tenant tenant = resolveTenant(httpRequest);
        String actor = actorOf(httpRequest);
        String code = action.getCode();

        actionRepository.delete(action);

        auditService.log(tenant, null, null, "ACTION_CHANGED",
                "Action " + code + " (id=" + id + ") deleted", actor, null);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(NotificationAction action, UpsertNotificationActionRequest request) {
        action.setCode(request.code());
        action.setDisplayName(request.displayName());
        action.setDescription(request.description());
        action.setEnabled(request.enabled() == null || request.enabled());
        action.setApprovalRequired(request.approvalRequired() != null && request.approvalRequired());
        action.setDefaultChannel(request.defaultChannel() != null && !request.defaultChannel().isBlank()
                ? request.defaultChannel() : "EMAIL");
        action.setIcon(request.icon());
        action.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        action.setRoleRequired(request.roleRequired());
        action.setFormSchemaId(request.formSchemaId());
        action.setAttachmentSchemaId(request.attachmentSchemaId());
        action.setHypersenseExposed(request.hypersenseExposed() != null && request.hypersenseExposed());
        action.setContextResolverKey(request.contextResolverKey());
    }

    /**
     * HyperSense's Analyst Actions panel cannot honor conditional field
     * visibility (see HS_NOTIFICATION_V2_METADATA_DESIGN.md) — a
     * hypersense_exposed action must therefore never point at a form_schema
     * containing a conditional field, or the panel would just render every
     * field flat regardless of the condition, silently breaking the intended
     * behavior.
     */
    private String validateHypersenseExposure(NotificationAction action) {
        if (!action.isHypersenseExposed() || action.getFormSchemaId() == null) {
            return null;
        }
        FormSchema schema = formSchemaRepository.findById(action.getFormSchemaId()).orElse(null);
        if (schema == null || schema.getFields() == null) {
            return null;
        }
        boolean hasConditionalField = schema.getFields().stream()
                .anyMatch(f -> f.getConditionalOnFieldKey() != null);
        if (hasConditionalField) {
            return "Form schema '" + schema.getName() + "' (id=" + schema.getId() + ") has conditional fields " +
                    "and cannot be linked to a hypersense_exposed action — HyperSense cannot render conditional visibility";
        }
        return null;
    }

    private NotificationAction requireAction(Long id) {
        return actionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification action not found: " + id));
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
