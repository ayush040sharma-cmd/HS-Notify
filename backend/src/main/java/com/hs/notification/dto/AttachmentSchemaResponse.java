package com.hs.notification.dto;

import com.hs.notification.model.AttachmentSchema;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public record AttachmentSchemaResponse(
        Long id,
        String name,
        String description,
        String createdBy,
        OffsetDateTime createdOn,
        List<String> providerKeys
) {
    public static AttachmentSchemaResponse from(AttachmentSchema schema) {
        List<String> keys = schema.getProviders() == null ? List.of() : schema.getProviders().stream()
                .sorted(Comparator.comparingInt(p -> p.getDisplayOrder()))
                .map(p -> p.getProviderKey())
                .toList();
        return new AttachmentSchemaResponse(schema.getId(), schema.getName(), schema.getDescription(),
                schema.getCreatedBy(), schema.getCreatedOn(), keys);
    }
}
