package com.hs.notification.service;

import com.hs.notification.dto.NotifyRequest;
import com.hs.notification.dto.SendCustomNotificationRequest;
import com.hs.notification.exception.FeatureDisabledException;
import com.hs.notification.exception.RateLimitExceededException;
import com.hs.notification.exception.RuleNotActiveException;
import com.hs.notification.model.*;
import com.hs.notification.repository.NotificationActionRepository;
import com.hs.notification.repository.NotificationJobRepository;
import com.hs.notification.repository.NotificationRuleRepository;
import com.hs.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final TemplateRenderingService templateRenderingService;
    private final AttachmentService attachmentService;
    private final PrRecordsExportService prRecordsExportService;
    private final MailDispatchService mailDispatchService;
    private final AuditService auditService;
    private final FeatureToggleService featureToggleService;
    private final RateLimitService rateLimitService;
    private final String caseLinkBaseUrl;
    private final String attachmentsStorageDir;

    public NotificationService(NotificationRuleRepository ruleRepository,
                               NotificationJobRepository jobRepository,
                               NotificationTemplateRepository templateRepository,
                               NotificationActionRepository actionRepository,
                               TemplateRenderingService templateRenderingService,
                               AttachmentService attachmentService,
                               PrRecordsExportService prRecordsExportService,
                               MailDispatchService mailDispatchService,
                               AuditService auditService,
                               FeatureToggleService featureToggleService,
                               RateLimitService rateLimitService,
                               @Value("${hs-notification.case-link.base-url}") String caseLinkBaseUrl,
                               @Value("${hs-notification.attachments.storage-path:${java.io.tmpdir}/hs-notification-attachments}") String attachmentsStorageDir) {
        this.ruleRepository = ruleRepository;
        this.jobRepository = jobRepository;
        this.templateRepository = templateRepository;
        this.actionRepository = actionRepository;
        this.templateRenderingService = templateRenderingService;
        this.attachmentService = attachmentService;
        this.prRecordsExportService = prRecordsExportService;
        this.mailDispatchService = mailDispatchService;
        this.auditService = auditService;
        this.featureToggleService = featureToggleService;
        this.rateLimitService = rateLimitService;
        this.caseLinkBaseUrl = caseLinkBaseUrl;
        this.attachmentsStorageDir = attachmentsStorageDir;
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

        List<String> toAddresses = recipientOverride != null && !recipientOverride.isBlank()
                ? List.of(recipientOverride)
                : resolveRecipients(rule.getRecipientGroup(), "TO");
        List<String> ccAddresses = resolveRecipients(rule.getRecipientGroup(), "CC");
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
                ? new NotifyRequest.AttachmentOptions(flags.includeCaseLink(), flags.includePrRecords(), flags.includeAttachment())
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

        List<String> to = request.recipients() != null && request.recipients().to() != null
                ? request.recipients().to() : List.of();
        if (to.isEmpty()) {
            throw new IllegalArgumentException("recipients.to is required for action " + action.getCode());
        }
        List<String> cc = request.recipients() != null && request.recipients().cc() != null
                ? request.recipients().cc() : List.of();
        List<String> bcc = request.recipients() != null && request.recipients().bcc() != null
                ? request.recipients().bcc() : List.of();

        Map<String, Object> effectiveContext = new HashMap<>(request.payload() == null ? Map.of() : request.payload());
        NotifyRequest.AttachmentOptions opts = request.attachmentOptions();
        boolean includeCaseLink = opts != null && Boolean.TRUE.equals(opts.includeCaseLink());
        boolean includePrRecords = opts != null && Boolean.TRUE.equals(opts.includePrRecords());
        boolean includeAttachment = opts != null && Boolean.TRUE.equals(opts.includeAttachment());

        String caseLink = null;
        if (includeCaseLink) {
            caseLink = computeCaseLink(effectiveContext.get("case_id"));
            if (caseLink != null) {
                effectiveContext.put("case_link", caseLink);
            }
        }

        String finalSubject;
        String finalBody;
        if (request.template() != null && !request.template().isBlank()) {
            NotificationTemplate template = templateRepository
                    .findByTenant_TenantIdAndTemplateCodeAndStatus(tenant.getTenantId(), request.template(), "ACTIVE")
                    .orElseThrow(() -> new IllegalArgumentException("Active template not found: " + request.template()));
            if (request.comment() != null) {
                effectiveContext.put("comment", request.comment());
            }
            TemplateRenderingService.RenderedContent rendered =
                    templateRenderingService.render(template, effectiveContext);
            finalSubject = rendered.subject();
            finalBody = rendered.body();
        } else if (request.body() != null && !request.body().isBlank()) {
            finalSubject = request.subject();
            finalBody = request.body();
            if (finalSubject == null || finalSubject.isBlank() || finalBody == null || finalBody.isBlank()) {
                throw new IllegalArgumentException("Either template or both subject and body are required");
            }
        } else {
            if (request.subject() == null || request.subject().isBlank()) {
                throw new IllegalArgumentException("subject is required");
            }
            finalSubject = request.subject();
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

        List<String> notices = new ArrayList<>();
        String prRecordsFailureReason = null;
        if (request.attachmentPath() != null && !request.attachmentPath().isBlank()) {
            job.setAttachmentPath(request.attachmentPath());
            job.setAttachmentStatus("GENERATED");
        } else if (includePrRecords) {
            prRecordsFailureReason = attachPrRecordsCsv(job, effectiveContext);
            notices.add(prRecordsFailureReason == null
                    ? "PR records CSV attached."
                    : "PR records CSV not attached: " + prRecordsFailureReason);
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

    /** Same UUID__displayName storage convention AttachmentUploadController uses, so EmailChannelSender derives the right filename. */
    private String writeToAttachmentStorage(byte[] content, String displayName) throws IOException {
        String safeName = displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = UUID.randomUUID() + "__" + safeName;
        Path dir = Path.of(attachmentsStorageDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(storedName);
        Files.write(target, content);
        return target.toAbsolutePath().toString();
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
}
