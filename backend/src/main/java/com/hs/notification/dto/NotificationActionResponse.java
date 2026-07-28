package com.hs.notification.dto;

import com.hs.notification.model.NotificationAction;

import java.time.OffsetDateTime;

public record NotificationActionResponse(
        Long id,
        String code,
        String displayName,
        String description,
        boolean enabled,
        boolean approvalRequired,
        String defaultChannel,
        String icon,
        int displayOrder,
        String roleRequired,
        Long formSchemaId,
        Long attachmentSchemaId,
        String createdBy,
        OffsetDateTime createdOn
) {
    public static NotificationActionResponse from(NotificationAction a) {
        return new NotificationActionResponse(
                a.getId(), a.getCode(), a.getDisplayName(), a.getDescription(),
                a.isEnabled(), a.isApprovalRequired(), a.getDefaultChannel(),
                a.getIcon(), a.getDisplayOrder(), a.getRoleRequired(),
                a.getFormSchemaId(), a.getAttachmentSchemaId(),
                a.getCreatedBy(), a.getCreatedOn());
    }
}
