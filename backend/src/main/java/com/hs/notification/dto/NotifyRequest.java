package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Unified send payload for POST /api/v1/notify — replaces send-direct,
 * send-custom, and (as an entry point) send-rule. action is resolved
 * against the notification_rule table first (rule-based send, using its
 * recipient group/attachment-rule/retry config), then against the
 * notification_action registry (ad-hoc send) — see
 * NotificationService.notify. The three legacy endpoints keep their own
 * request shapes for backward compatibility and translate into this record
 * internally rather than disappearing.
 */
public record NotifyRequest(
        @NotBlank String action,
        String channel,
        String template,
        Recipients recipients,
        Map<String, Object> payload,
        AttachmentOptions attachmentOptions,
        String subject,
        /** Raw HTML body, used verbatim when present and no template is given (send-direct legacy style). */
        String body,
        /** Free-text comment, wrapped in a paragraph when present and neither template nor body is given (send-custom legacy style). */
        String comment,
        /** Pre-generated/uploaded file path to attach as-is (send-direct legacy style) — takes priority over attachmentOptions.includePrRecords. */
        String attachmentPath,
        String idempotencyKey,
        String sourceReference,
        String sourceType
) {
    public record Recipients(List<String> to, List<String> cc, List<String> bcc) {}

    public record AttachmentOptions(Boolean includeCaseLink, Boolean includePrRecords, Boolean includeAttachment) {}
}
