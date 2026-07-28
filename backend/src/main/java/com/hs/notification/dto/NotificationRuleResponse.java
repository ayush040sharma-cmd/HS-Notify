package com.hs.notification.dto;

import com.hs.notification.model.NotificationRule;

import java.time.OffsetDateTime;

public record NotificationRuleResponse(
        Long ruleId,
        String ruleCode,
        String triggerEvent,
        String triggerSource,
        TemplateRef template,
        String recipientGroupCode,
        String recipientMode,
        String fallbackRecipientGroupCode,
        String escalationChainCode,
        Integer maxRetryCount,
        Integer retryBackoffSeconds,
        Double retryBackoffMultiplier,
        String onFinalFailure,
        String attachmentType,
        String attachmentReportIdentifier,
        String attachmentOutputFormat,
        String attachmentOnFailure,
        String status,
        boolean active,
        String createdBy,
        String approvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record TemplateRef(String templateCode, String channel) {}

    public static NotificationRuleResponse from(NotificationRule rule) {
        TemplateRef template = rule.getTemplate() != null
                ? new TemplateRef(rule.getTemplate().getTemplateCode(), rule.getTemplate().getChannel())
                : null;
        String recipientGroupCode = rule.getRecipientGroup() != null ? rule.getRecipientGroup().getGroupCode() : null;
        String fallbackRecipientGroupCode = rule.getFallbackRecipientGroup() != null ? rule.getFallbackRecipientGroup().getGroupCode() : null;
        String escalationChainCode = rule.getEscalationChain() != null ? rule.getEscalationChain().getChainCode() : null;

        String attachmentType = "NONE";
        String attachmentReportIdentifier = null;
        String attachmentOutputFormat = null;
        String attachmentOnFailure = null;
        if (rule.getAttachmentRule() != null) {
            var attachmentRule = rule.getAttachmentRule();
            attachmentType = switch (attachmentRule.getAttachmentSource()) {
                case "REPORT_SERVICE" -> "PR_RECORD";
                case "STATIC_FILE" -> "UPLOAD";
                case "GENERATED_PDF" -> "GENERATED_PDF";
                default -> "NONE";
            };
            attachmentReportIdentifier = attachmentRule.getReportIdentifier();
            attachmentOutputFormat = attachmentRule.getOutputFormat();
            attachmentOnFailure = attachmentRule.getOnGenerationFailure();
        }

        return new NotificationRuleResponse(
                rule.getRuleId(),
                rule.getRuleCode(),
                rule.getTriggerEvent(),
                rule.getTriggerSource(),
                template,
                recipientGroupCode,
                rule.getRecipientMode(),
                fallbackRecipientGroupCode,
                escalationChainCode,
                rule.getMaxRetryCount(),
                rule.getRetryBackoffSeconds(),
                rule.getRetryBackoffMultiplier(),
                rule.getOnFinalFailure(),
                attachmentType,
                attachmentReportIdentifier,
                attachmentOutputFormat,
                attachmentOnFailure,
                rule.getStatus(),
                rule.isActive(),
                rule.getCreatedBy(),
                rule.getApprovedBy(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
