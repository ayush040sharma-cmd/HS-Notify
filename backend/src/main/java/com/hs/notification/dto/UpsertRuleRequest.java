package com.hs.notification.dto;

import java.util.List;

public record UpsertRuleRequest(
        String ruleCode,
        String triggerEvent,
        String triggerSource,
        String templateCode,
        String recipientGroupCode,
        List<String> toEmails,
        List<String> ccEmails,
        /** STATIC_GROUP (default) | CURRENT_USER — see NotificationService.resolveCurrentUserRecipient. */
        String recipientMode,
        /** Only meaningful when recipientMode=CURRENT_USER — used when no acting-user/case-owner identity resolves. */
        String fallbackRecipientGroupCode,
        String escalationChainCode,
        Integer maxRetryCount,
        Integer retryBackoffSeconds,
        Double retryBackoffMultiplier,
        String onFinalFailure,
        String attachmentType,
        String attachmentReportIdentifier,
        String attachmentOutputFormat,
        String attachmentOnFailure
) {}
