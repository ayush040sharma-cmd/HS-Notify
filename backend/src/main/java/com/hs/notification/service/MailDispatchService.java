package com.hs.notification.service;

import com.hs.notification.model.NotificationJob;
import com.hs.notification.model.NotificationRule;
import com.hs.notification.repository.NotificationJobRepository;
import com.hs.notification.service.channel.ChannelSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MailDispatchService.class);

    private final Map<String, ChannelSender> senders;
    private final NotificationJobRepository jobRepository;
    private final AuditService auditService;
    private final EscalationService escalationService;

    public MailDispatchService(List<ChannelSender> channelSenders,
                               NotificationJobRepository jobRepository,
                               AuditService auditService,
                               EscalationService escalationService) {
        this.senders = channelSenders.stream()
                .collect(Collectors.toMap(ChannelSender::channel, Function.identity()));
        this.jobRepository = jobRepository;
        this.auditService = auditService;
        this.escalationService = escalationService;
    }

    @Transactional
    public void attemptSend(NotificationJob job) {
        if ("FAILED".equals(job.getStatus())) {
            auditService.log(job.getTenant(), job, job.getRule(), "SEND_SKIPPED",
                    "Job already FAILED before send attempt (likely attachment failure)");
            jobRepository.save(job);
            return;
        }

        String channel = resolveChannel(job);
        ChannelSender sender = senders.getOrDefault(channel, senders.get("EMAIL"));
        if (sender == null) {
            log.error("No sender registered for channel={} and EMAIL fallback missing for job={}", channel, job.getJobId());
            job.setStatus("FAILED");
            job.setLastError("No channel sender registered for: " + channel);
            jobRepository.save(job);
            return;
        }

        job.setStatus("SENDING");
        job.setAttemptCount(job.getAttemptCount() + 1);
        long startTime = System.currentTimeMillis();

        try {
            sender.send(job);
            int elapsed = (int) (System.currentTimeMillis() - startTime);

            job.setStatus("SENT");
            job.setSentAt(OffsetDateTime.now());
            jobRepository.save(job);

            String ccSuffix = (job.getCcAddresses() != null && !job.getCcAddresses().isEmpty())
                    ? " cc " + job.getCcAddresses() : "";
            auditService.log(job.getTenant(), job, job.getRule(), "SEND_SUCCESS",
                    "Delivered via " + channel + " to " + job.getToAddresses() + ccSuffix, "system", elapsed);

        } catch (Exception e) {
            int elapsed = (int) (System.currentTimeMillis() - startTime);
            log.warn("Send attempt {} failed for job {} via {}: {}",
                    job.getAttemptCount(), job.getJobId(), channel, e.getMessage());

            job.setLastError(e.getMessage());
            auditService.log(job.getTenant(), job, job.getRule(), "SEND_FAILED",
                    e.getMessage(), "system", elapsed);

            handleFailure(job);
        }
    }

    private String resolveChannel(NotificationJob job) {
        if (job.getRule() != null
                && job.getRule().getTemplate() != null
                && job.getRule().getTemplate().getChannel() != null) {
            return job.getRule().getTemplate().getChannel();
        }
        if (job.getChannel() != null && !job.getChannel().isBlank()) {
            return job.getChannel();
        }
        return "EMAIL";
    }

    private void handleFailure(NotificationJob job) {
        NotificationRule rule = job.getRule();
        int maxRetries = rule != null ? rule.getMaxRetryCount() : job.getMaxRetryCount();

        if (job.getAttemptCount() < maxRetries) {
            int backoffSeconds = computeBackoff(rule, job.getAttemptCount());
            job.setStatus("RETRYING");
            job.setNextRetryAt(OffsetDateTime.now().plusSeconds(backoffSeconds));
            jobRepository.save(job);

            auditService.log(job.getTenant(), job, rule, "RETRY_SCHEDULED",
                    "Retry " + (job.getAttemptCount() + 1) + "/" + maxRetries +
                            " scheduled in " + backoffSeconds + "s");
        } else {
            job.setStatus("FAILED");
            jobRepository.save(job);

            String onFinalFailure = rule != null ? rule.getOnFinalFailure() : "ESCALATE";
            if ("ESCALATE".equals(onFinalFailure) && rule != null && rule.getEscalationChain() != null) {
                job.setStatus("ESCALATED");
                jobRepository.save(job);
                escalationService.escalate(job, rule.getEscalationChain().getSteps());
                auditService.log(job.getTenant(), job, rule, "ESCALATED",
                        "Max retries exhausted, escalation chain triggered");
            } else if ("HOLD_FOR_MANUAL".equals(onFinalFailure)) {
                job.setStatus("FAILED");
                jobRepository.save(job);
                auditService.log(job.getTenant(), job, rule, "SEND_FAILED",
                        "Max retries exhausted, held for manual review");
            } else {
                auditService.log(job.getTenant(), job, rule, "SEND_FAILED",
                        "Max retries exhausted, dropped per policy");
            }
        }
    }

    int computeBackoff(NotificationRule rule, int attemptCount) {
        if (rule == null) return 60 * attemptCount;
        double base = rule.getRetryBackoffSeconds();
        double multiplier = rule.getRetryBackoffMultiplier();
        return (int) (base * Math.pow(multiplier, attemptCount - 1));
    }
}
