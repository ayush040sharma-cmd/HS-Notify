package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Ad-hoc/custom notification payload from HyperSense PAS/BMS — doesn't map to
 * a pre-approved rule. scenario must be one of FRAUD_ALERT, ZERO_TOLERANCE,
 * VENDOR_EMAIL, CASE_SUMMARY, PR_CLOSE (validated in NotificationService,
 * used for audit-log categorization only — it doesn't otherwise change
 * behavior).
 */
public record SendCustomNotificationRequest(
        @NotBlank String scenario,
        @NotEmpty List<String> toAddresses,
        List<String> ccAddresses,
        @NotBlank String subject,
        String comment,
        String templateCode,
        Map<String, Object> context,
        Flags flags
) {
    /**
     * includePrRecords and includeAttachment are accepted but stubbed for now —
     * see NotificationService.submitCustomNotification and the returned notices.
     */
    public record Flags(Boolean includeCaseLink, Boolean includePrRecords, Boolean includeAttachment) {}
}
