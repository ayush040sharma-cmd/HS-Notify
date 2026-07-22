package com.hs.notification.dto;

import com.hs.notification.model.NotificationTemplate;

import java.time.OffsetDateTime;
import java.util.List;

public record NotificationTemplateResponse(
        Long templateId,
        String templateCode,
        Integer version,
        String channel,
        String subjectTemplate,
        String bodyTemplate,
        List<String> allowedVariables,
        List<String> piiMaskFields,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static NotificationTemplateResponse from(NotificationTemplate t) {
        return new NotificationTemplateResponse(
                t.getTemplateId(), t.getTemplateCode(), t.getVersion(), t.getChannel(),
                t.getSubjectTemplate(), t.getBodyTemplate(),
                t.getAllowedVariables(), t.getPiiMaskFields(),
                t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
