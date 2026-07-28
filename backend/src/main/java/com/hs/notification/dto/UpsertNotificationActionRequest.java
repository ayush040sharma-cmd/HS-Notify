package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertNotificationActionRequest(
        @NotBlank String code,
        @NotBlank String displayName,
        String description,
        Boolean enabled,
        Boolean approvalRequired,
        String defaultChannel,
        String icon,
        Integer displayOrder,
        String roleRequired,
        Long formSchemaId,
        Long attachmentSchemaId
) {}
