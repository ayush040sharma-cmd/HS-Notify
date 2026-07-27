package com.hs.notification.controller;

import com.hs.notification.dto.ActiveTemplateResponse;
import com.hs.notification.dto.NotificationTemplateResponse;
import com.hs.notification.dto.UpsertTemplateRequest;
import com.hs.notification.model.NotificationTemplate;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.NotificationRuleRepository;
import com.hs.notification.repository.NotificationTemplateRepository;
import com.hs.notification.repository.TenantRepository;
import com.hs.notification.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private static final Logger log = LoggerFactory.getLogger(TemplateController.class);

    private final NotificationTemplateRepository templateRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;
    private final NotificationRuleRepository ruleRepository;

    public TemplateController(NotificationTemplateRepository templateRepository,
                              TenantRepository tenantRepository,
                              AuditService auditService,
                              NotificationRuleRepository ruleRepository) {
        this.templateRepository = templateRepository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.ruleRepository = ruleRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationTemplateResponse>> list(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        return ResponseEntity.ok(templateRepository.findByTenant_TenantId(tenant.getTenantId())
                .stream().map(NotificationTemplateResponse::from).toList());
    }

    /**
     * Unauthenticated (beyond the shared lookup token — see LookupTokenFilter)
     * lookup for HyperSense's PAS analyst action screen dropdown. Kept small
     * and flat. tenantCode is optional for now — omit it to get all tenants,
     * but every unscoped call is logged so we can see who still needs to pass
     * it once PAS callers are updated.
     */
    @GetMapping("/active")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ActiveTemplateResponse>> listActive(
            @RequestParam(required = false) String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            log.warn("GET /api/v1/templates/active called without tenantCode — returning all tenants");
            return ResponseEntity.ok(templateRepository.findByStatus("ACTIVE").stream()
                    .map(ActiveTemplateResponse::from).toList());
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenantCode: " + tenantCode));
        return ResponseEntity.ok(templateRepository.findByTenant_TenantIdAndStatus(tenant.getTenantId(), "ACTIVE")
                .stream().map(ActiveTemplateResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<NotificationTemplateResponse> create(@RequestBody UpsertTemplateRequest request,
                                                               HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        NotificationTemplate template = new NotificationTemplate();
        template.setTenant(tenant);
        template.setCreatedBy(actorOf(httpRequest));
        applyFields(template, request, true);
        templateRepository.save(template);

        auditService.log(tenant, null, null, "TEMPLATE_CHANGED",
                "Template " + template.getTemplateCode() + " created as " + template.getStatus(),
                actorOf(httpRequest), null);
        return ResponseEntity.ok(NotificationTemplateResponse.from(template));
    }

    @PutMapping("/{templateId}")
    @Transactional
    public ResponseEntity<NotificationTemplateResponse> update(@PathVariable Long templateId,
                                                               @RequestBody UpsertTemplateRequest request,
                                                               HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        NotificationTemplate template = templateRepository.findById(templateId)
                .filter(t -> t.getTenant().getTenantId().equals(tenant.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        applyFields(template, request, false);
        templateRepository.save(template);

        auditService.log(tenant, null, null, "TEMPLATE_CHANGED",
                "Template " + template.getTemplateCode() + " edited", actorOf(httpRequest), null);
        return ResponseEntity.ok(NotificationTemplateResponse.from(template));
    }

    /**
     * Activates a PENDING_REVIEW template. Kept separate from the generic update()
     * path (which refuses to set status=ACTIVE directly) so activation always goes
     * through the maker-checker check below, matching RuleController.approve().
     */
    @PostMapping("/{templateId}/approve")
    @Transactional
    public ResponseEntity<?> approve(
            @PathVariable Long templateId,
            @RequestHeader(value = "X-Operator-Username", required = false) String operatorUsername,
            HttpServletRequest httpRequest) {

        Tenant tenant = resolveTenant(httpRequest);
        NotificationTemplate template = templateRepository.findById(templateId)
                .filter(t -> t.getTenant().getTenantId().equals(tenant.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        String approver = (operatorUsername != null && !operatorUsername.isBlank())
                ? operatorUsername
                : (httpRequest.getRemoteUser() != null ? httpRequest.getRemoteUser() : "approver");

        if (approver.equalsIgnoreCase(template.getCreatedBy())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Maker-checker violation: approver and creator cannot be the same person",
                    "createdBy", template.getCreatedBy(),
                    "approver", approver));
        }

        template.setStatus("ACTIVE");
        template.setApprovedBy(approver);
        template.setApprovedAt(OffsetDateTime.now());
        templateRepository.save(template);

        auditService.log(tenant, null, null, "TEMPLATE_CHANGED",
                "Template " + template.getTemplateCode() + " approved and activated by " + approver,
                approver, null);
        return ResponseEntity.ok(NotificationTemplateResponse.from(template));
    }

    @DeleteMapping("/{templateId}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long templateId, HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        NotificationTemplate template = templateRepository.findById(templateId)
                .filter(t -> t.getTenant().getTenantId().equals(tenant.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (ruleRepository.existsByTemplate_TemplateId(templateId)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Template is used by one or more rules and cannot be deleted",
                    "templateId", templateId));
        }

        String templateCode = template.getTemplateCode();
        templateRepository.delete(template);

        auditService.log(tenant, null, null, "TEMPLATE_CHANGED",
                "Template " + templateCode + " (id=" + templateId + ") deleted", actorOf(httpRequest), null);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(NotificationTemplate template, UpsertTemplateRequest request, boolean isCreate) {
        template.setTemplateCode(request.templateCode());
        template.setChannel(request.channel() != null ? request.channel() : "EMAIL");
        template.setSubjectTemplate(request.subjectTemplate());
        template.setBodyTemplate(request.bodyTemplate());
        template.setAllowedVariables(request.allowedVariables() != null ? request.allowedVariables() : List.of());
        template.setPiiMaskFields(request.piiMaskFields() != null ? request.piiMaskFields() : List.of());

        String requestedStatus = request.status();
        if (requestedStatus == null) {
            if (isCreate) template.setStatus("DRAFT");
            // else: leave the existing status untouched on a plain edit
        } else if ("ACTIVE".equals(requestedStatus) && !"ACTIVE".equals(template.getStatus())) {
            throw new IllegalArgumentException(
                    "Use POST /templates/{id}/approve to activate a template — it enforces maker-checker");
        } else {
            template.setStatus(requestedStatus);
        }
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
