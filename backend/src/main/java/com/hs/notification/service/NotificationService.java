package com.hs.notification.service;

import com.hs.notification.dto.NotifyRequest;
import com.hs.notification.dto.SendCustomNotificationRequest;
import com.hs.notification.exception.FeatureDisabledException;
import com.hs.notification.exception.FormValidationException;
import com.hs.notification.exception.RateLimitExceededException;
import com.hs.notification.exception.RuleNotActiveException;
import com.hs.notification.model.*;
import com.hs.notification.repository.AppUserRepository;
import com.hs.notification.repository.FormSchemaRepository;
import com.hs.notification.repository.NotificationActionRepository;
import com.hs.notification.repository.NotificationJobRepository;
import com.hs.notification.repository.NotificationRuleRepository;
import com.hs.notification.repository.NotificationTemplateRepository;
import com.hs.notification.service.attachment.AttachmentOrchestrationService;
import com.hs.notification.service.attachment.AttachmentStorageWriter;
import com.hs.notification.service.directory.UserDirectoryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRuleRepository ruleRepository;
    private final NotificationJobRepository jobRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationActionRepository actionRepository;
    private final FormSchemaRepository formSchemaRepository;
    private final FormFieldValidationService formFieldValidationService;
    private final AppUserRepository appUserRepository;
    private final UserDirectoryResolver userDirectoryResolver;
    private final TemplateRenderingService templateRenderingService;
    private final AttachmentService attachmentService;
    private final PrRecordsExportService prRecordsExportService;
    private final AttachmentOrchestrationService attachmentOrchestrationService;
    private final ObjectRegistryResolver objectRegistryResolver;
    private final AttachmentStorageWriter attachmentStorageWriter;
    private final MailDispatchService mailDispatchService;
    private final AuditService auditService;
    private final FeatureToggleService featureToggleService;
    private final RateLimitService rateLimitService;
    private final String caseLinkBaseUrl;

    public NotificationService(NotificationRuleRepository ruleRepository,
                               NotificationJobRepository jobRepository,
                               NotificationTemplateRepository templateRepository,
                               NotificationActionRepository actionRepository,
                               FormSchemaRepository formSchemaRepository,
                               FormFieldValidationService formFieldValidationService,
                               AppUserRepository appUserRepository,
                               UserDirectoryResolver userDirectoryResolver,
                               TemplateRenderingService templateRenderingService,
                               AttachmentService attachmentService,
                               PrRecordsExportService prRecordsExportService,
                               AttachmentOrchestrationService attachmentOrchestrationService,
                               ObjectRegistryResolver objectRegistryResolver,
                               AttachmentStorageWriter attachmentStorageWriter,
                               MailDispatchService mailDispatchService,
                               AuditService auditService,
                               FeatureToggleService featureToggleService,
                               RateLimitService rateLimitService,
                               @Value("${hs-notification.case-link.base-url}") String caseLinkBaseUrl) {
        this.ruleRepository = ruleRepository;
        this.jobRepository = jobRepository;
        this.templateRepository = templateRepository;
        this.actionRepository = actionRepository;
        this.formSchemaRepository = formSchemaRepository;
        this.formFieldValidationService = formFieldValidationService;
        this.appUserRepository = appUserRepository;
        this.userDirectoryResolver = userDirectoryResolver;
        this.templateRenderingService = templateRenderingService;
        this.attachmentService = attachmentService;
        this.prRecordsExportService = prRecordsExportService;
        this.attachmentOrchestrationService = attachmentOrchestrationService;
        this.objectRegistryResolver = objectRegistryResolver;
        this.attachmentStorageWriter = attachmentStorageWriter;
        this.mailDispatchService = mailDispatchService;
        this.auditService = auditService;
        this.featureToggleService = featureToggleService;
        this.rateLimitService = rateLimitService;
        this.caseLinkBaseUrl = caseLinkBaseUrl;
    }

    /**
     * Adds template variables that are derived at send time rather than supplied
     * by the caller — e.g. case_link, built from case_id, is never stored on the
     * case row itself. Returns a new map so the job's persisted context_json
     * keeps only what the caller actually sent.
     */
    private Map<String, Object> withComputedVariables(Map<String, Object> context) {
        Map<String, Object> enriched = new HashMap<>(context == null ? Map.of() : context);
        String caseLink = computeCaseLink(enriched.get("case_id"));
        if (caseLink != null) {
            enriched.put("case_link", caseLink);
        }
        return enriched;
    }

    /** Single source of truth for case_link — reused by every send path that offers it. */
    private String computeCaseLink(Object caseId) {
        if (caseId == null || caseId.toString().isBlank()) return null;
        return caseLinkBaseUrl + "/" + caseId;
    }

    private static String asString(Object o) {
        return (o == null || o.toString().isBlank()) ? null : o.toString();
    }

    @Transactional
    public NotificationJob submitRuleBasedNotification(Tenant tenant, String ruleCode,
                                                         String idempotencyKey,
                                                         String sourceReference, String sourceType,
                                                         Map<String, Object> context,
                                                         String recipientOverride) {

        Optional<NotificationJob> existing = jobRepository
                .findByTenant_TenantIdAndIdempotencyKey(tenant.getTenantId(), idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay detected for key={}, returning existing job={}",
                    idempotencyKey, existing.get().getJobId());
            return existing.get();
        }

        if (!featureToggleService.isNotificationsEnabled(tenant.getTenantId())) {
            throw new FeatureDisabledException("Notifications disabled for tenant " + tenant.getTenantCode());
        }

        NotificationRule rule = ruleRepository
                .findByTenant_TenantIdAndRuleCode(tenant.getTenantId(), ruleCode)
                .orElseThrow(() -> new RuleNotActiveException("Rule not found: " + ruleCode));

        if (!rule.isActive() || !"ACTIVE".equals(rule.getStatus())) {
            throw new RuleNotActiveException("Rule " + ruleCode + " is not ACTIVE (status=" + rule.getStatus() + ")");
        }

        if (!rateLimitService.tryAcquire(tenant.getTenantId(), rule.getRuleId())) {
            throw new RateLimitExceededException("Rate limit exceeded for rule " + ruleCode);
        }

        NotificationJob job = new NotificationJob();
        job.setTenant(tenant);
        job.setRule(rule);
        job.setIdempotencyKey(idempotencyKey);
        job.setSourceReference(sourceReference);
        job.setSourceType(sourceType);
        job.setContextJson(context);
        job.setMaxRetryCount(rule.getMaxRetryCount());
        job.setStatus("PENDING");

        List<String> toAddresses = resolveToAddresses(rule, recipientOverride, context);
        RecipientGroup ccSource = rule.getRecipientGroup() != null ? rule.getRecipientGroup() : rule.getFallbackRecipientGroup();
        List<String> ccAddresses = resolveRecipients(ccSource, "CC");
        job.setToAddresses(toAddresses);
        job.setCcAddresses(ccAddresses);

        TemplateRenderingService.RenderedContent rendered =
                templateRenderingService.render(rule.getTemplate(), withComputedVariables(context));
        job.setSubject(rendered.subject());
        job.setRenderedBody(rendered.body());

        job = jobRepository.save(job);
        auditService.log(tenant, job, rule, "JOB_CREATED",
                "Job created from trigger=" + rule.getTriggerEvent() + " source=" + sourceReference);

        if (rule.getAttachmentRule() != null && rule.getAttachmentRule().isActive()) {
            attachmentService.attachIfConfigured(job, rule.getAttachmentRule(), context);
        } else {
            job.setAttachmentStatus("NOT_APPLICABLE");
        }

        mailDispatchService.attemptSend(job);

        return job;
    }

    /**
     * Manual/direct send from the operational UI. Predates the notification_action
     * registry — has no scenario/action concept of its own — so this translates
     * into a NotifyRequest using the reserved DIRECT_SEND action (seeded by the
     * V9 migration, enabled by default) and delegates to notify(), rather than
     * duplicating send logic. See notify() for the actual implementation.
     */
    @Transactional
    public NotificationJob submitDirectNotification(Tenant tenant, List<String> to, List<String> cc,
                                                      String templateCode, Map<String, Object> context,
                                                      String subject, String htmlBody,
                                                      String attachmentPath, String actor) {
        // notify()'s freeform branch is more lenient (send-custom's legacy contract
        // tolerates an empty body) — preserve send-direct's original stricter
        // validation and exact message here, before delegating.
        if ((templateCode == null || templateCode.isBlank())
                && (subject == null || subject.isBlank() || htmlBody == null || htmlBody.isBlank())) {
            throw new IllegalArgumentException("Either templateCode or both subject and htmlBody are required");
        }

        NotifyRequest request = new NotifyRequest(
                "DIRECT_SEND", null, templateCode,
                new NotifyRequest.Recipients(to, cc, null),
                context, null,
                subject, htmlBody, null, attachmentPath,
                null, null, null);
        return notify(tenant, request, actor).job();
    }

    /**
     * Ad-hoc/custom sends triggered from HyperSense PAS/BMS — a flexible payload
     * that doesn't map to a pre-approved rule. Translates into a NotifyRequest
     * and delegates to notify() rather than duplicating send logic — see
     * notify() for the actual implementation.
     */
    @Transactional
    public CustomSendResult submitCustomNotification(Tenant tenant, SendCustomNotificationRequest request, String actor) {
        SendCustomNotificationRequest.Flags flags = request.flags();
        NotifyRequest.AttachmentOptions attachmentOptions = flags != null
                ? new NotifyRequest.AttachmentOptions(flags.includeCaseLink(), flags.includePrRecords(), flags.includeAttachment(), null)
                : null;

        NotifyRequest notifyRequest = new NotifyRequest(
                request.scenario(), null, request.templateCode(),
                new NotifyRequest.Recipients(request.toAddresses(), request.ccAddresses(), null),
                request.context(), attachmentOptions,
                request.subject(), null, request.comment(), null,
                null, null, null);

        NotifyResult result = notify(tenant, notifyRequest, actor);
        return new CustomSendResult(result.job(), result.notices());
    }

    /**
     * The unified send engine behind POST /api/v1/notify (and, internally,
     * every other send path except rule-based, which stays entirely inside
     * submitRuleBasedNotification since notify() itself delegates to it —
     * routing an already-rule-shaped request back through notify() would be
     * circular). action is resolved against notification_rule first (by
     * ruleCode — reuses the existing, well-tested rule pipeline unchanged,
     * recipients.to[0] optionally overriding the TO list exactly like
     * submitRuleBasedNotification's recipientOverride), then against the
     * notification_action registry (ad-hoc send, this method's own logic
     * below). Unknown/disabled in both places → 400.
     */
    @Transactional
    public NotifyResult notify(Tenant tenant, NotifyRequest request, String actor) {
        Optional<NotificationRule> rule = ruleRepository
                .findByTenant_TenantIdAndRuleCode(tenant.getTenantId(), request.action());
        if (rule.isPresent()) {
            String recipientOverride = (request.recipients() != null
                    && request.recipients().to() != null
                    && !request.recipients().to().isEmpty())
                    ? request.recipients().to().get(0) : null;
            String idempotencyKey = request.idempotencyKey() != null
                    ? request.idempotencyKey() : UUID.randomUUID().toString();
            NotificationJob job = submitRuleBasedNotification(
                    tenant, request.action(), idempotencyKey,
                    request.sourceReference(), request.sourceType(),
                    request.payload() != null ? request.payload() : Map.of(),
                    recipientOverride);
            return new NotifyResult(job, List.of());
        }

        NotificationAction action = actionRepository.findByCode(request.action())
                .filter(NotificationAction::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or disabled action: " + request.action()));

        // --- Normalize flat vs nested field namespaces ------------------------------
        // A caller may supply subject/recipient either at the top level (direct-API
        // style) or inside payload under the form-field keys "subject"/"to_address"
        // (HyperSense/PAS flat-field style). Bridge BOTH directions once here so the
        // caller only has to provide each value in one place.
        Map<String, Object> payload = new HashMap<>(request.payload() == null ? Map.of() : request.payload());

        // top-level -> payload (so a top-level-only caller still passes form validation)
        if (asString(request.subject()) != null) {
            payload.putIfAbsent("subject", request.subject());
        }
        if (request.recipients() != null && request.recipients().to() != null
                && !request.recipients().to().isEmpty()) {
            payload.putIfAbsent("to_address", request.recipients().to().get(0));
        }

        // payload -> top-level (so a payload-only caller still passes the send checks)
        String effectiveSubject = asString(request.subject()) != null
                ? request.subject()
                : asString(payload.get("subject"));
        List<String> effectiveTo = (request.recipients() != null && request.recipients().to() != null
                && !request.recipients().to().isEmpty())
                ? request.recipients().to()
                : (asString(payload.get("to_address")) != null
                    ? List.of(asString(payload.get("to_address")))
                    : List.of());
        // ---------------------------------------------------------------------------

        if (action.getFormSchemaId() != null) {
            FormSchema formSchema = formSchemaRepository.findById(action.getFormSchemaId()).orElse(null);
            // field_validation was previously client-side-only metadata, rendered by the
            // dashboard wizard but never checked here — a caller that skips the wizard
            // (a direct API call, or eventually HyperSense itself) could submit anything.
            // formSchema == null just means a dangling reference; that's a config problem
            // to fix in the admin UI, not a reason to reject every send against this action.
            if (formSchema != null) {
                List<FormFieldValidationService.ValidationError> errors =
                        formFieldValidationService.validate(formSchema, payload);
                if (!errors.isEmpty()) {
                    String detail = errors.stream()
                            .map(e -> e.fieldKey() + ": " + e.message())
                            .collect(Collectors.joining("; "));
                    throw new FormValidationException(detail);
                }
            }
        }

        if (!featureToggleService.isNotificationsEnabled(tenant.getTenantId())) {
            throw new FeatureDisabledException("Notifications disabled for tenant " + tenant.getTenantCode());
        }

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<NotificationJob> existing = jobRepository
                    .findByTenant_TenantIdAndIdempotencyKey(tenant.getTenantId(), request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent replay detected for key={}, returning existing job={}",
                        request.idempotencyKey(), existing.get().getJobId());
                return new NotifyResult(existing.get(), List.of());
            }
        }

        List<String> to = effectiveTo;
        if (to.isEmpty()) {
            throw new IllegalArgumentException("recipients.to (or payload.to_address) is required for action " + action.getCode());
        }
        List<String> cc = request.recipients() != null && request.recipients().cc() != null
                ? request.recipients().cc() : List.of();
        List<String> bcc = request.recipients() != null && request.recipients().bcc() != null
                ? request.recipients().bcc() : List.of();

        Map<String, Object> effectiveContext = new HashMap<>(payload);
        NotifyRequest.AttachmentOptions opts = request.attachmentOptions();
        boolean includeCaseLink = opts != null && Boolean.TRUE.equals(opts.includeCaseLink());
        boolean includePrRecords = opts != null && Boolean.TRUE.equals(opts.includePrRecords());
        boolean includeAttachment = opts != null && Boolean.TRUE.equals(opts.includeAttachment());
        List<String> requestedProviders = opts != null && opts.providers() != null ? opts.providers() : List.of();

        String caseLink = null;
        if (includeCaseLink) {
            caseLink = computeCaseLink(effectiveContext.get("case_id"));
            if (caseLink != null) {
                effectiveContext.put("case_link", caseLink);
            }
        }

        String finalSubject;
        String finalBody;
        NotificationTemplate templateUsed = null;
        if (request.template() != null && !request.template().isBlank()) {
            NotificationTemplate template = templateRepository
                    .findByTenant_TenantIdAndTemplateCodeAndStatus(tenant.getTenantId(), request.template(), "ACTIVE")
                    .orElseThrow(() -> new IllegalArgumentException("Active template not found: " + request.template()));
            templateUsed = template;
            if (request.comment() != null) {
                effectiveContext.put("comment", request.comment());
            }
            TemplateRenderingService.RenderedContent rendered =
                    templateRenderingService.render(template, effectiveContext);
            finalSubject = rendered.subject();
            finalBody = rendered.body();
        } else if (request.body() != null && !request.body().isBlank()) {
            finalSubject = effectiveSubject;
            finalBody = request.body();
            if (finalSubject == null || finalSubject.isBlank() || finalBody == null || finalBody.isBlank()) {
                throw new IllegalArgumentException("Either template or both subject and body are required");
            }
        } else {
            if (effectiveSubject == null || effectiveSubject.isBlank()) {
                throw new IllegalArgumentException("subject (or payload.subject) is required");
            }
            finalSubject = effectiveSubject;
            StringBuilder body = new StringBuilder();
            if (request.comment() != null && !request.comment().isBlank()) {
                body.append("<p>").append(escapeHtml(request.comment())).append("</p>");
            }
            if (caseLink != null) {
                body.append("<p><a href=\"").append(caseLink).append("\">View this case in HyperSense</a></p>");
            }
            finalBody = body.toString();
        }

        NotificationJob job = new NotificationJob();
        job.setTenant(tenant);
        job.setToAddresses(to);
        job.setCcAddresses(cc);
        job.setBccAddresses(bcc);
        job.setChannel(request.channel() != null && !request.channel().isBlank() ? request.channel() : action.getDefaultChannel());
        job.setSubject(finalSubject);
        job.setRenderedBody(finalBody);
        job.setContextJson(effectiveContext);
        job.setMaxRetryCount(1);
        job.setStatus("PENDING");
        job.setAttachmentStatus("NOT_APPLICABLE");

        // Attachment providers read the raw context map directly rather than through
        // {{variable}} interpolation, so the template's allowed_variables whitelist
        // doesn't apply the same way here — but pii_mask_fields still should: a field
        // flagged as PII shouldn't reach attachment generation unmasked just because
        // it arrived via a different code path than subject/body rendering.
        Map<String, Object> attachmentContext = templateUsed != null
                ? templateRenderingService.maskPiiForAttachments(templateUsed, effectiveContext)
                : effectiveContext;

        List<String> notices = new ArrayList<>();
        String prRecordsFailureReason = null;
        if (request.attachmentPath() != null && !request.attachmentPath().isBlank()) {
            job.setAttachmentPath(request.attachmentPath());
            job.setAttachmentStatus("GENERATED");
        } else if (!requestedProviders.isEmpty()) {
            // Phase 4: multi-provider path — takes priority over the legacy
            // includePrRecords boolean when providers is explicitly given.
            // Old callers never set this field, so their behavior is untouched.
            var bundle = attachmentOrchestrationService.generateBundle(requestedProviders, tenant, attachmentContext);
            notices.addAll(bundle.notices());
            if (bundle.path() != null) {
                job.setAttachmentPath(bundle.path());
                job.setAttachmentStatus("GENERATED");
            } else {
                job.setAttachmentStatus("FAILED");
                job.setLastError("No attachments could be generated: " + String.join("; ", bundle.notices()));
            }
        } else if (includePrRecords) {
            prRecordsFailureReason = attachPrRecordsCsv(job, attachmentContext);
            notices.add(prRecordsFailureReason == null
                    ? "PR records CSV attached."
                    : "PR records CSV not attached: " + prRecordsFailureReason);
        } else {
            // Object Registry auto-routing (V16__notification_object_registry):
            // only reached when the caller didn't already say what to attach.
            // Exact-match on sourceType against notification_object_registry —
            // see ObjectRegistryResolver for why this never guesses.
            Optional<String> autoProviderKey = objectRegistryResolver.resolveAttachmentProviderKey(request.sourceType());
            if (autoProviderKey.isPresent()) {
                var bundle = attachmentOrchestrationService.generateBundle(List.of(autoProviderKey.get()), tenant, attachmentContext);
                notices.addAll(bundle.notices());
                if (bundle.path() != null) {
                    job.setAttachmentPath(bundle.path());
                    job.setAttachmentStatus("GENERATED");
                } else {
                    job.setAttachmentStatus("FAILED");
                    job.setLastError("No attachments could be generated: " + String.join("; ", bundle.notices()));
                }
            }
        }
        if (includeAttachment) {
            notices.add("includeAttachment was set, but attachment generation (e.g. dashboard snapshot) isn't wired yet — no attachment was generated for this send.");
        }

        job = jobRepository.save(job);

        String detail = "Notify (action=" + action.getCode() + ")"
                + (request.template() != null ? ", template=" + request.template() : "");
        auditService.log(tenant, job, null, "JOB_CREATED", detail, actor, null);

        if (prRecordsFailureReason != null) {
            auditService.log(tenant, job, null, "ATTACHMENT_FAILED",
                    "PR records CSV not attached: " + prRecordsFailureReason, actor, null);
        }

        mailDispatchService.attemptSend(job);
        return new NotifyResult(job, notices);
    }

    public record NotifyResult(NotificationJob job, List<String> notices) {}

    /**
     * Mirrors the production quarantine script: never blocks the send. Sets the
     * job's attachment fields directly (GENERATED + path, or FAILED + reason)
     * and returns null on success or a human-readable failure reason otherwise.
     */
    private String attachPrRecordsCsv(NotificationJob job, Map<String, Object> context) {
        Object caseId = context.get("case_id");
        Object catalogId = context.get("catalog_id");

        PrRecordsExportService.ExportResult result = prRecordsExportService.export(caseId, catalogId);
        if (!result.success()) {
            job.setAttachmentStatus("FAILED");
            job.setLastError("PR records CSV not attached: " + result.failureReason());
            return result.failureReason();
        }

        Object caseTemplateName = context.get("case_template_name");
        String displayName = caseTemplateName + "_" + caseId + ".csv";

        try {
            String path = writeToAttachmentStorage(result.csvBytes(), displayName);
            job.setAttachmentPath(path);
            job.setAttachmentStatus("GENERATED");
            return null;
        } catch (IOException e) {
            log.warn("Failed to write PR records CSV to attachment storage for case_id={}: {}", caseId, e.getMessage());
            job.setAttachmentStatus("FAILED");
            job.setLastError("PR records CSV not attached: failed to write file: " + e.getMessage());
            return "failed to write file: " + e.getMessage();
        }
    }

    private String writeToAttachmentStorage(byte[] content, String displayName) throws IOException {
        return attachmentStorageWriter.write(content, displayName);
    }

    private String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record CustomSendResult(NotificationJob job, List<String> notices) {}

    private List<String> resolveRecipients(RecipientGroup group, String type) {
        if (group == null || group.getMembers() == null) return List.of();
        return group.getMembers().stream()
                .filter(m -> m.isActive() && type.equalsIgnoreCase(m.getRecipientType()))
                .map(RecipientGroupMember::getEmailAddress)
                .filter(addr -> addr != null && !addr.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * recipientOverride (explicit, e.g. from notify()'s recipients.to[0]) always
     * wins, matching the existing override semantics. Otherwise STATIC_GROUP
     * rules behave exactly as before; CURRENT_USER rules resolve dynamically —
     * see resolveCurrentUserRecipient.
     */
    private List<String> resolveToAddresses(NotificationRule rule, String recipientOverride, Map<String, Object> context) {
        if (recipientOverride != null && !recipientOverride.isBlank()) {
            return List.of(recipientOverride);
        }
        if ("CURRENT_USER".equals(rule.getRecipientMode())) {
            return resolveCurrentUserRecipient(rule, context);
        }
        return resolveRecipients(rule.getRecipientGroup(), "TO");
    }

    /**
     * Resolution order for a CURRENT_USER rule:
     *  1. context.acting_user_email — caller already knows the acting user's email.
     *  2. context.acting_username — looked up against app_user.email (our own
     *     dashboard login table, for sends triggered from HS Notify's own
     *     wizard), then against UserDirectoryResolver (HyperSense's directory
     *     mirror, for usernames HyperSense itself would send — see
     *     HS_NOTIFICATION_V2_METADATA_DESIGN.md).
     *  3. context.case_owner_email — for paths with no acting user (e.g. the
     *     case-watch scheduler); populated by whatever polls case_tbl once a
     *     real owner/assigned-analyst column is known there.
     *  4. rule.fallbackRecipientGroup's TO members, if configured.
     * Nothing resolvable is a hard failure, not a silent empty send.
     */
    private List<String> resolveCurrentUserRecipient(NotificationRule rule, Map<String, Object> context) {
        Object actingEmail = context.get("acting_user_email");
        if (actingEmail != null && !actingEmail.toString().isBlank()) {
            return List.of(actingEmail.toString());
        }

        Object actingUsername = context.get("acting_username");
        if (actingUsername != null && !actingUsername.toString().isBlank()) {
            String username = actingUsername.toString();
            Optional<AppUser> user = appUserRepository.findByUsername(username);
            if (user.isPresent() && user.get().getEmail() != null && !user.get().getEmail().isBlank()) {
                return List.of(user.get().getEmail());
            }
            Optional<String> directoryEmail = userDirectoryResolver.resolveEmail(username);
            if (directoryEmail.isPresent()) {
                return List.of(directoryEmail.get());
            }
            log.warn("CURRENT_USER recipient: acting_username={} has no resolvable email in app_user or " +
                    "the user directory, falling through", username);
        }

        Object caseOwnerEmail = context.get("case_owner_email");
        if (caseOwnerEmail != null && !caseOwnerEmail.toString().isBlank()) {
            return List.of(caseOwnerEmail.toString());
        }

        if (rule.getFallbackRecipientGroup() != null) {
            List<String> fallback = resolveRecipients(rule.getFallbackRecipientGroup(), "TO");
            if (!fallback.isEmpty()) return fallback;
        }

        throw new IllegalArgumentException("CURRENT_USER recipient could not be resolved for rule " +
                rule.getRuleCode() + " — no acting_user_email/acting_username/case_owner_email in context " +
                "and no fallback_recipient_group configured");
    }
}
