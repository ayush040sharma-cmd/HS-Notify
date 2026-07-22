package com.hs.notification.dto;

import com.hs.notification.model.NotificationTemplate;

import java.util.List;

public record ActiveTemplateResponse(
        Long id,
        String templateCode,
        String subjectTemplate,
        List<String> allowedVariables
) {
    public static ActiveTemplateResponse from(NotificationTemplate t) {
        return new ActiveTemplateResponse(
                t.getTemplateId(), t.getTemplateCode(), t.getSubjectTemplate(), t.getAllowedVariables());
    }
}
