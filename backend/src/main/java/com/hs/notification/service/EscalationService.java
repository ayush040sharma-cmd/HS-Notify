package com.hs.notification.service;

import com.hs.notification.model.EscalationChainStep;
import com.hs.notification.model.NotificationJob;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final JavaMailSender mailSender;

    public EscalationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void escalate(NotificationJob job, List<EscalationChainStep> steps) {
        if (steps == null || steps.isEmpty()) {
            log.warn("Job {} marked ESCALATED but no escalation chain steps configured", job.getJobId());
            return;
        }

        EscalationChainStep firstStep = steps.get(0);
        sendEscalationEmail(job, firstStep.getRecipientEmail());
    }

    private void sendEscalationEmail(NotificationJob job, String recipient) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setTo(recipient);
            helper.setSubject("[ESCALATION] Notification delivery failed - job " + job.getJobId());
            helper.setText(
                    "<p>A notification job failed after exhausting all retries.</p>" +
                    "<p><b>Job ID:</b> " + job.getJobId() + "<br/>" +
                    "<b>Source:</b> " + job.getSourceType() + " " + job.getSourceReference() + "<br/>" +
                    "<b>Intended recipients:</b> " + job.getToAddresses() + "<br/>" +
                    "<b>Last error:</b> " + job.getLastError() + "</p>" +
                    "<p>Please review in the Notification Platform job queue.</p>", true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send escalation email for job {}", job.getJobId(), e);
        }
    }
}
