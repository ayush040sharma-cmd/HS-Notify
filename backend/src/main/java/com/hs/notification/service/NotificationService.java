package com.hs.notification.service;

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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRuleRepository ruleRepository;
    private final NotificationJobRepository jobRepository;
    private final NotificationTemplateRepository templateRepository;
    private final TemplateRenderingService templateRenderingService;
    private final AttachmentService attachmentService;
    private final MailDispatchService mailDispatchService;
    private final AuditService auditService;
    private final FeatureToggleService featureToggleService;
    private final RateLimitService rateLimitService;
    private final String caseLinkBaseUrl;

    public NotificationService(NotificationRuleRepository ruleRepository,
                               NotificationJobRepository jobRepository,
                               NotificationTemplateRepository templateRepository,
                               TemplateRenderingService templateRenderingService,
                               AttachmentService attachmentService,
                               MailDispatchService mailDispatchService,
                               AuditService auditService,
                               FeatureToggleService featureToggleService,
                               RateLimitService rateLimitService,
                               @Value("${hs-notification.case-link.base-url}") String caseLinkBaseUrl) {
        this.ruleRepository = ruleRepository;
        this.jobRepository = jobRepository;
        this.templateRepository = templateRepository;
        this.templateRenderingService = templateRenderingService;
        this.attachmentService = attachmentService;
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
        Object caseId = enriched.get("case_id");
        if (caseId != null && !caseId.toString().isBlank()) {
            enriched.put("case_link", caseLinkBaseUrl + "/" + caseId);
        }
        return enriched;
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

    private List<String> resolveRecipients(RecipientGroup group, String type) {
        if (group == null || group.getMembers() == null) return List.of();
        return group.getMembers().stream()
                .filter(m -> m.isActive() && type.equalsIgnoreCase(m.getRecipientType()))
                .map(RecipientGroupMember::getEmailAddress)
                .filter(addr -> addr != null && !addr.isBlank())
                .collect(Collectors.toList());
    }
}
