package com.hs.notification.service;

import com.hs.notification.dto.SendCustomNotificationRequest;
import com.hs.notification.exception.FeatureDisabledException;
import com.hs.notification.exception.RateLimitExceededException;
import com.hs.notification.exception.RuleNotActiveException;
import com.hs.notification.model.*;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Set<String> ALLOWED_CUSTOM_SCENARIOS = Set.of(
            "FRAUD_ALERT", "ZERO_TOLERANCE", "VENDOR_EMAIL", "CASE_SUMMARY", "PR_CLOSE");

    private final NotificationRuleRepository ruleRepository;
    private final NotificationJobRepository jobRepository;
    private final NotificationTemplateRepository templateRepository;
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

    @Transactional
    public NotificationJob submitDirectNotification(Tenant tenant, List<String> to, List<String> cc,
                                                      String templateCode, Map<String, Object> context,
                                                      String subject, String htmlBody,
                                                      String attachmentPath, String actor) {
        if (!featureToggleService.isNotificationsEnabled(tenant.getTenantId())) {
            throw new FeatureDisabledException("Notifications disabled for tenant " + tenant.getTenantCode());
        }

        String finalSubject = subject;
        String finalBody = htmlBody;
        if (templateCode != null && !templateCode.isBlank()) {
            NotificationTemplate template = templateRepository
                    .findByTenant_TenantIdAndTemplateCodeAndStatus(tenant.getTenantId(), templateCode, "ACTIVE")
                    .orElseThrow(() -> new IllegalArgumentException("Active template not found: " + templateCode));
            TemplateRenderingService.RenderedContent rendered =
                    templateRenderingService.render(template, withComputedVariables(context));
            finalSubject = rendered.subject();
            finalBody = rendered.body();
        }

        if (finalSubject == null || finalSubject.isBlank() || finalBody == null || finalBody.isBlank()) {
            throw new IllegalArgumentException("Either templateCode or both subject and htmlBody are required");
        }

        NotificationJob job = new NotificationJob();
        job.setTenant(tenant);
        job.setToAddresses(to);
        job.setCcAddresses(cc == null ? List.of() : cc);
        job.setSubject(finalSubject);
        job.setRenderedBody(finalBody);
        job.setMaxRetryCount(1);
        job.setStatus("PENDING");

        if (attachmentPath != null && !attachmentPath.isBlank()) {
            job.setAttachmentPath(attachmentPath);
            job.setAttachmentStatus("GENERATED");
        } else {
            job.setAttachmentStatus("NOT_APPLICABLE");
        }

        job = jobRepository.save(job);

        String detail = templateCode != null ? "Manual/direct send (template=" + templateCode + ")" : "Manual/direct send";
        auditService.log(tenant, job, null, "JOB_CREATED", detail, actor, null);
        mailDispatchService.attemptSend(job);
        return job;
    }

    /**
     * Ad-hoc/custom sends triggered from HyperSense PAS/BMS — a flexible payload
     * that doesn't map to a pre-approved rule. Deliberately reuses the same job
     * persistence, MailDispatchService dispatch (and therefore RetryScheduler),
     * and AuditService logging as every other send path; this is not a parallel
     * pipeline. includePrRecords fetches a PR-records CSV from the usage DB via
     * PrRecordsExportService — never blocks the send on failure (see
     * attachPrRecordsCsv). includeAttachment (e.g. dashboard snapshot) is still
     * stubbed — flagged back in CustomSendResult.notices rather than silently
     * ignored.
     */
    @Transactional
    public CustomSendResult submitCustomNotification(Tenant tenant, SendCustomNotificationRequest request, String actor) {
        if (!ALLOWED_CUSTOM_SCENARIOS.contains(request.scenario())) {
            throw new IllegalArgumentException(
                    "Unknown scenario: " + request.scenario() + " — must be one of " + ALLOWED_CUSTOM_SCENARIOS);
        }
        if (!featureToggleService.isNotificationsEnabled(tenant.getTenantId())) {
            throw new FeatureDisabledException("Notifications disabled for tenant " + tenant.getTenantCode());
        }

        Map<String, Object> effectiveContext = new HashMap<>(request.context() == null ? Map.of() : request.context());
        SendCustomNotificationRequest.Flags flags = request.flags();
        boolean includeCaseLink = flags != null && Boolean.TRUE.equals(flags.includeCaseLink());
        boolean includePrRecords = flags != null && Boolean.TRUE.equals(flags.includePrRecords());
        boolean includeAttachment = flags != null && Boolean.TRUE.equals(flags.includeAttachment());

        String caseLink = null;
        if (includeCaseLink) {
            caseLink = computeCaseLink(effectiveContext.get("case_id"));
            if (caseLink != null) {
                effectiveContext.put("case_link", caseLink);
            }
        }

        String finalBody;
        if (request.templateCode() != null && !request.templateCode().isBlank()) {
            NotificationTemplate template = templateRepository
                    .findByTenant_TenantIdAndTemplateCodeAndStatus(tenant.getTenantId(), request.templateCode(), "ACTIVE")
                    .orElseThrow(() -> new IllegalArgumentException("Active template not found: " + request.templateCode()));
            if (request.comment() != null) {
                effectiveContext.put("comment", request.comment());
            }
            finalBody = templateRenderingService.render(template, effectiveContext).body();
        } else {
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
        job.setToAddresses(request.toAddresses());
        job.setCcAddresses(request.ccAddresses() == null ? List.of() : request.ccAddresses());
        job.setSubject(request.subject());
        job.setRenderedBody(finalBody);
        job.setContextJson(effectiveContext);
        job.setMaxRetryCount(1);
        job.setStatus("PENDING");
        job.setAttachmentStatus("NOT_APPLICABLE");

        List<String> notices = new ArrayList<>();
        String prRecordsFailureReason = null;
        if (includePrRecords) {
            prRecordsFailureReason = attachPrRecordsCsv(job, effectiveContext);
            if (prRecordsFailureReason == null) {
                notices.add("PR records CSV attached.");
            } else {
                notices.add("PR records CSV not attached: " + prRecordsFailureReason);
            }
        }
        if (includeAttachment) {
            notices.add("includeAttachment was set, but attachment generation (e.g. dashboard snapshot) isn't wired yet — no attachment was generated for this send.");
        }

        job = jobRepository.save(job);

        String detail = "Custom send (scenario=" + request.scenario() + ")"
                + (request.templateCode() != null ? ", template=" + request.templateCode() : "");
        auditService.log(tenant, job, null, "JOB_CREATED", detail, actor, null);

        if (prRecordsFailureReason != null) {
            auditService.log(tenant, job, null, "ATTACHMENT_FAILED",
                    "PR records CSV not attached: " + prRecordsFailureReason, actor, null);
        }

        mailDispatchService.attemptSend(job);
        return new CustomSendResult(job, notices);
    }

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
