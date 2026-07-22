package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Payload sent by an Alarm/Case/Analyst closure action (or the action
 * adapter) to trigger a rule-based notification.
 */
public record SendNotificationRequest(
        @NotBlank String ruleCode,
        @NotBlank String idempotencyKey,
        String sourceReference,
        String sourceType,
        Map<String, Object> context,
        String recipientOverride
) {}
