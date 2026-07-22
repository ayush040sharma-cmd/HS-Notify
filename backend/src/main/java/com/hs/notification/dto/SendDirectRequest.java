package com.hs.notification.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Payload for manual/direct sends from the operational UI. Either supply
 * subject+htmlBody directly, or templateCode+context to render from a
 * template (same DLP whitelist/PII masking rule-based sends use).
 */
public record SendDirectRequest(
        @NotEmpty List<String> to,
        List<String> cc,
        String subject,
        String htmlBody,
        String templateCode,
        Map<String, Object> context,
        String attachmentPath
) {}
