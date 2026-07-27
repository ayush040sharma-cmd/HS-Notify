package com.hs.notification.controller;

import com.hs.notification.dto.ActiveRuleResponse;
import com.hs.notification.dto.NotificationRuleResponse;
import com.hs.notification.dto.RuleFormOptionsResponse;
import com.hs.notification.dto.UpsertRuleRequest;
import com.hs.notification.model.AttachmentRule;
import com.hs.notification.model.EscalationChain;
import com.hs.notification.model.NotificationRule;
import com.hs.notification.model.NotificationTemplate;
import com.hs.notification.model.RecipientGroup;
import com.hs.notification.model.RecipientGroupMember;
import com.hs.notification.model.Tenant;
import com.hs.notification.repository.AttachmentRuleRepository;
import com.hs.notification.repository.EscalationChainRepository;
import com.hs.notification.repository.NotificationAuditLogRepository;
import com.hs.notification.repository.NotificationJobRepository;
import com.hs.notification.repository.NotificationRuleRepository;
import com.hs.notification.repository.NotificationTemplateRepository;
import com.hs.notification.repository.RateLimitBucketRepository;
import com.hs.notification.repository.RecipientGroupRepository;
import com.hs.notification.repository.TenantRepository;
import com.hs.notification.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private static final Logger log = LoggerFactory.getLogger(RuleController.class);

    private final NotificationRuleRepository ruleRepository;
    private final NotificationTemplateRepository templateRepository;
    private final RecipientGroupRepository recipientGroupRepository;
    private final EscalationChainRepository escalationChainRepository;
    private final AttachmentRuleRepository attachmentRuleRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;
    private final NotificationJobRepository jobRepository;
    private final NotificationAuditLogRepository auditLogRepository;
    private final RateLimitBucketRepository rateLimitBucketRepository;

    public RuleController(NotificationRuleRepository ruleRepository,
                          NotificationTemplateRepository templateRepository,
                          RecipientGroupRepository recipientGroupRepository,
                          EscalationChainRepository escalationChainRepository,
                          AttachmentRuleRepository attachmentRuleRepository,
                          TenantRepository tenantRepository,
                          AuditService auditService,
                          NotificationJobRepository jobRepository,
                          NotificationAuditLogRepository auditLogRepository,
                          RateLimitBucketRepository rateLimitBucketRepository) {
        this.ruleRepository = ruleRepository;
        this.templateRepository = templateRepository;
        this.recipientGroupRepository = recipientGroupRepository;
        this.escalationChainRepository = escalationChainRepository;
        this.attachmentRuleRepository = attachmentRuleRepository;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.jobRepository = jobRepository;
        this.auditLogRepository = auditLogRepository;
        this.rateLimitBucketRepository = rateLimitBucketRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationRuleResponse>> list(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        return ResponseEntity.ok(ruleRepository.findByTenant_TenantId(tenant.getTenantId())
                .stream().map(NotificationRuleResponse::from).toList());
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
    public ResponseEntity<List<ActiveRuleResponse>> listActive(
            @RequestParam(required = false) String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            log.warn("GET /api/v1/rules/active called without tenantCode — returning all tenants");
            return ResponseEntity.ok(ruleRepository.findByStatus("ACTIVE").stream()
                    .map(ActiveRuleResponse::from).toList());
        }

        Tenant tenant = tenantRepository.findByTenantCode(tenantCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenantCode: " + tenantCode));
        return ResponseEntity.ok(ruleRepository.findByTenant_TenantIdAndStatus(tenant.getTenantId(), "ACTIVE")
                .stream().map(ActiveRuleResponse::from).toList());
    }

    @GetMapping("/form-options")
    @Transactional(readOnly = true)
    public ResponseEntity<RuleFormOptionsResponse> formOptions(HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        Long tenantId = tenant.getTenantId();

        List<RuleFormOptionsResponse.TemplateOption> templates = templateRepository
                .findByTenant_TenantId(tenantId).stream()
                .map(t -> new RuleFormOptionsResponse.TemplateOption(t.getTemplateCode(), t.getChannel(), t.getStatus()))
                .toList();

        List<RuleFormOptionsResponse.RecipientGroupOption> recipientGroups = recipientGroupRepository
                .findByTenant_TenantId(tenantId).stream()
                .map(g -> new RuleFormOptionsResponse.RecipientGroupOption(g.getGroupCode(), g.getDescription()))
                .toList();

        List<RuleFormOptionsResponse.EscalationChainOption> escalationChains = escalationChainRepository
                .findByTenant_TenantId(tenantId).stream()
                .map(c -> new RuleFormOptionsResponse.EscalationChainOption(c.getChainCode(), c.getDescription()))
                .toList();

        return ResponseEntity.ok(new RuleFormOptionsResponse(templates, recipientGroups, escalationChains));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<NotificationRuleResponse> create(@RequestBody UpsertRuleRequest request,
                                                           HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        NotificationRule rule = new NotificationRule();
        rule.setTenant(tenant);
        rule.setCreatedBy(actorOf(httpRequest));
        rule.setStatus("DRAFT");
        rule.setActive(false);
        applyFields(rule, request, tenant);
        ruleRepository.save(rule);

        auditService.log(tenant, null, rule, "RULE_CHANGED", "Created as DRAFT", actorOf(httpRequest), null);
        return ResponseEntity.ok(NotificationRuleResponse.from(rule));
    }

    @PutMapping("/{ruleId}")
    @Transactional
    public ResponseEntity<?> update(@PathVariable Long ruleId, @RequestBody UpsertRuleRequest request,
                                     HttpServletRequest httpRequest) {
        NotificationRule rule = requireRule(ruleId, httpRequest);

        if ("ACTIVE".equals(rule.getStatus())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Rule is ACTIVE — disable it before editing, then resubmit for review",
                    "ruleId", ruleId));
        }

        Tenant tenant = rule.getTenant();
        applyFields(rule, request, tenant);
        ruleRepository.save(rule);

        auditService.log(tenant, null, rule, "RULE_CHANGED", "Edited", actorOf(httpRequest), null);
        return ResponseEntity.ok(NotificationRuleResponse.from(rule));
    }

    private void applyFields(NotificationRule rule, UpsertRuleRequest request, Tenant tenant) {
        rule.setRuleCode(request.ruleCode());
        rule.setTriggerEvent(request.triggerEvent());
        rule.setTriggerSource(request.triggerSource());

        NotificationTemplate template = templateRepository
                .findByTenant_TenantIdAndTemplateCodeOrderByVersionDesc(tenant.getTenantId(), request.templateCode())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + request.templateCode()));
        rule.setTemplate(template);

        RecipientGroup recipientGroup = resolveRecipientGroup(request, tenant);
        rule.setRecipientGroup(recipientGroup);

        if (request.escalationChainCode() != null && !request.escalationChainCode().isBlank()) {
            EscalationChain chain = escalationChainRepository
                    .findByTenant_TenantIdAndChainCode(tenant.getTenantId(), request.escalationChainCode())
                    .orElseThrow(() -> new IllegalArgumentException("Escalation chain not found: " + request.escalationChainCode()));
            rule.setEscalationChain(chain);
        } else {
            rule.setEscalationChain(null);
        }

        rule.setMaxRetryCount(request.maxRetryCount() != null ? request.maxRetryCount() : 3);
        rule.setRetryBackoffSeconds(request.retryBackoffSeconds() != null ? request.retryBackoffSeconds() : 60);
        rule.setRetryBackoffMultiplier(request.retryBackoffMultiplier() != null ? request.retryBackoffMultiplier() : 2.0);
        rule.setOnFinalFailure(request.onFinalFailure() != null ? request.onFinalFailure() : "ESCALATE");

        rule.setAttachmentRule(resolveAttachmentRule(request, tenant, rule.getAttachmentRule()));
    }

    /**
     * Maps the simplified picker options (PR Record / Upload File / None) the rule
     * editor exposes onto the existing attachment_rule row backing this rule —
     * REPORT_SERVICE and STATIC_FILE were already supported by AttachmentService,
     * this just gives the operator a way to configure them without touching the DB.
     */
    private AttachmentRule resolveAttachmentRule(UpsertRuleRequest request, Tenant tenant, AttachmentRule existing) {
        String type = request.attachmentType() == null || request.attachmentType().isBlank()
                ? "NONE" : request.attachmentType();

        if ("NONE".equals(type)) {
            return null;
        }

        AttachmentRule attachmentRule = existing != null ? existing : new AttachmentRule();
        attachmentRule.setTenant(tenant);
        attachmentRule.setRuleCode(request.ruleCode());
        attachmentRule.setActive(true);
        attachmentRule.setOnGenerationFailure(
                request.attachmentOnFailure() != null ? request.attachmentOnFailure() : "SEND_WITHOUT_ATTACHMENT");

        switch (type) {
            case "PR_RECORD" -> {
                if (request.attachmentReportIdentifier() == null || request.attachmentReportIdentifier().isBlank()) {
                    throw new IllegalArgumentException("attachmentReportIdentifier is required for PR_RECORD attachments");
                }
                attachmentRule.setAttachmentSource("REPORT_SERVICE");
                attachmentRule.setReportIdentifier(request.attachmentReportIdentifier());
                attachmentRule.setOutputFormat("PDF");
            }
            case "UPLOAD" -> {
                if (request.attachmentReportIdentifier() == null || request.attachmentReportIdentifier().isBlank()) {
                    throw new IllegalArgumentException("Upload a file first (attachmentReportIdentifier is the returned path)");
                }
                attachmentRule.setAttachmentSource("STATIC_FILE");
                attachmentRule.setReportIdentifier(request.attachmentReportIdentifier());
                attachmentRule.setOutputFormat(
                        request.attachmentOutputFormat() != null ? request.attachmentOutputFormat() : "PDF");
            }
            default -> throw new IllegalArgumentException("Unknown attachmentType: " + type);
        }

        return attachmentRuleRepository.save(attachmentRule);
    }

    /**
     * Either use an existing named recipient group, or — if the caller typed in
     * To/CC addresses directly instead of picking a group — find-or-create a
     * dedicated group for this rule and replace its member list.
     */
    private RecipientGroup resolveRecipientGroup(UpsertRuleRequest request, Tenant tenant) {
        if (request.recipientGroupCode() != null && !request.recipientGroupCode().isBlank()) {
            return recipientGroupRepository
                    .findByTenant_TenantIdAndGroupCode(tenant.getTenantId(), request.recipientGroupCode())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient group not found: " + request.recipientGroupCode()));
        }

        if (request.toEmails() == null || request.toEmails().isEmpty()) {
            throw new IllegalArgumentException("Provide either recipientGroupCode or toEmails");
        }

        String autoGroupCode = request.ruleCode() + "_RECIPIENTS";
        RecipientGroup group = recipientGroupRepository
                .findByTenant_TenantIdAndGroupCode(tenant.getTenantId(), autoGroupCode)
                .orElseGet(() -> {
                    RecipientGroup g = new RecipientGroup();
                    g.setTenant(tenant);
                    g.setGroupCode(autoGroupCode);
                    g.setDescription("Auto-managed recipients for rule " + request.ruleCode());
                    g.setMembers(new ArrayList<>());
                    return g;
                });

        List<RecipientGroupMember> members = new ArrayList<>();
        members.addAll(toMembers(group, request.toEmails(), "TO"));
        if (request.ccEmails() != null) {
            members.addAll(toMembers(group, request.ccEmails(), "CC"));
        }

        if (group.getMembers() == null) {
            group.setMembers(members);
        } else {
            group.getMembers().clear();
            group.getMembers().addAll(members);
        }

        return recipientGroupRepository.save(group);
    }

    private List<RecipientGroupMember> toMembers(RecipientGroup group, List<String> emails, String type) {
        List<RecipientGroupMember> members = new ArrayList<>();
        for (String email : emails) {
            if (email == null || email.isBlank()) continue;
            RecipientGroupMember member = new RecipientGroupMember();
            member.setRecipientGroup(group);
            member.setRecipientType(type);
            member.setEmailAddress(email.trim());
            member.setActive(true);
            members.add(member);
        }
        return members;
    }

    private String actorOf(HttpServletRequest request) {
        return request.getRemoteUser() != null ? request.getRemoteUser() : "operator";
    }

    @PostMapping("/{ruleId}/submit-for-review")
    @Transactional
    public ResponseEntity<NotificationRuleResponse> submitForReview(@PathVariable Long ruleId, HttpServletRequest httpRequest) {
        NotificationRule rule = requireRule(ruleId, httpRequest);
        rule.setStatus("PENDING_REVIEW");
        ruleRepository.save(rule);
        auditService.log(rule.getTenant(), null, rule, "RULE_CHANGED", "Submitted for review");
        return ResponseEntity.ok(NotificationRuleResponse.from(rule));
    }

    @PostMapping("/{ruleId}/approve")
    @Transactional
    public ResponseEntity<?> approve(
            @PathVariable Long ruleId,
            @RequestHeader(value = "X-Operator-Username", required = false) String operatorUsername,
            HttpServletRequest httpRequest) {

        NotificationRule rule = requireRule(ruleId, httpRequest);

        String approver = (operatorUsername != null && !operatorUsername.isBlank())
                ? operatorUsername
                : (httpRequest.getRemoteUser() != null ? httpRequest.getRemoteUser() : "approver");

        if (approver.equalsIgnoreCase(rule.getCreatedBy())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Maker-checker violation: approver and creator cannot be the same person",
                    "createdBy", rule.getCreatedBy(),
                    "approver", approver));
        }

        rule.setStatus("ACTIVE");
        rule.setActive(true);
        rule.setApprovedBy(approver);
        rule.setApprovedAt(OffsetDateTime.now());
        ruleRepository.save(rule);

        auditService.log(rule.getTenant(), null, rule, "RULE_CHANGED",
                "Approved and activated by " + approver, approver, null);
        return ResponseEntity.ok(NotificationRuleResponse.from(rule));
    }

    @PostMapping("/{ruleId}/disable")
    @Transactional
    public ResponseEntity<NotificationRuleResponse> disable(@PathVariable Long ruleId, HttpServletRequest httpRequest) {
        NotificationRule rule = requireRule(ruleId, httpRequest);
        rule.setActive(false);
        rule.setStatus("DISABLED");
        ruleRepository.save(rule);
        auditService.log(rule.getTenant(), null, rule, "RULE_CHANGED", "Disabled");
        return ResponseEntity.ok(NotificationRuleResponse.from(rule));
    }

    @DeleteMapping("/{ruleId}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long ruleId, HttpServletRequest httpRequest) {
        NotificationRule rule = requireRule(ruleId, httpRequest);

        if (jobRepository.existsByRule_RuleId(ruleId) || auditLogRepository.existsByRule_RuleId(ruleId)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Rule has audit/send history and cannot be deleted — disable it instead",
                    "ruleId", ruleId));
        }

        String ruleCode = rule.getRuleCode();
        rateLimitBucketRepository.deleteByRule_RuleId(ruleId);
        ruleRepository.delete(rule);

        auditService.log(rule.getTenant(), null, null, "RULE_CHANGED",
                "Rule " + ruleCode + " (id=" + ruleId + ") deleted", actorOf(httpRequest), null);
        return ResponseEntity.noContent().build();
    }

    private NotificationRule requireRule(Long ruleId, HttpServletRequest httpRequest) {
        Tenant tenant = resolveTenant(httpRequest);
        return ruleRepository.findById(ruleId)
                .filter(r -> r.getTenant().getTenantId().equals(tenant.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleId));
    }

    private Tenant resolveTenant(HttpServletRequest request) {
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        if (tenant == null) throw new IllegalStateException("Tenant not resolved");
        return tenant;
    }
}
