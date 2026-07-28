package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Ad-hoc/custom notification payload from HyperSense PAS/BMS — doesn't map to
 * a pre-approved rule. scenario must match an enabled code in the
 * notification_action registry (see NotificationActionController /
 * NotificationService.submitCustomNotification) — no longer a fixed set of
 * Java constants, so new scenarios can be added via the actions API without
 * a code change.
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
     * includePrRecords fetches a real PR-records CSV (see PrRecordsExportService).
     * includeAttachment (e.g. dashboard snapshot) is still stubbed — see
     * NotificationService.submitCustomNotification and the returned notices.
     */
    public record Flags(Boolean includeCaseLink, Boolean includePrRecords, Boolean includeAttachment) {}
}
