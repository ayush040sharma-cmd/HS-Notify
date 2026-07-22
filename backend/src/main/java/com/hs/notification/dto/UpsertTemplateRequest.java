package com.hs.notification.dto;

import java.util.List;

public record UpsertTemplateRequest(
        String templateCode,
        String channel,
        String subjectTemplate,
        String bodyTemplate,
        List<String> allowedVariables,
        List<String> piiMaskFields,
        String status
) {}
