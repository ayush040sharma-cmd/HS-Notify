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
