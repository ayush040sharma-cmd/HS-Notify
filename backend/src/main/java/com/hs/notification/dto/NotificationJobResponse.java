package com.hs.notification.dto;

import com.hs.notification.model.NotificationJob;

import java.time.OffsetDateTime;
import java.util.List;

public record NotificationJobResponse(
        Long jobId,
        String ruleCode,
        String channel,
        String status,
        List<String> toAddresses,
        List<String> ccAddresses,
        List<String> bccAddresses,
        String subject,
        String attachmentStatus,
        Integer attemptCount,
        Integer maxRetryCount,
        OffsetDateTime nextRetryAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt
) {
    public static NotificationJobResponse from(NotificationJob job) {
        String channel;
        if (job.getRule() != null && job.getRule().getTemplate() != null && job.getRule().getTemplate().getChannel() != null) {
            channel = job.getRule().getTemplate().getChannel();
        } else if (job.getChannel() != null && !job.getChannel().isBlank()) {
            channel = job.getChannel();
        } else {
            channel = "EMAIL";
        }
        return new NotificationJobResponse(
                job.getJobId(),
                job.getRule() != null ? job.getRule().getRuleCode() : null,
                channel,
                job.getStatus(),
                job.getToAddresses(),
                job.getCcAddresses(),
                job.getBccAddresses(),
                job.getSubject(),
                job.getAttachmentStatus(),
                job.getAttemptCount(),
                job.getMaxRetryCount(),
                job.getNextRetryAt(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getSentAt()
        );
    }
}
