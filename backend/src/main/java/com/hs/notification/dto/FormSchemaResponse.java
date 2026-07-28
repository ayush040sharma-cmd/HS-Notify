package com.hs.notification.dto;

import com.hs.notification.model.FormSchema;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public record FormSchemaResponse(
        Long id,
        String name,
        String description,
        int version,
        String createdBy,
        OffsetDateTime createdOn,
        OffsetDateTime updatedOn,
        List<FormFieldDto> fields
) {
    public static FormSchemaResponse from(FormSchema schema) {
        List<FormFieldDto> fields = schema.getFields() == null ? List.of() : schema.getFields().stream()
                .sorted(Comparator.comparingInt(f -> f.getDisplayOrder()))
                .map(FormFieldDto::from)
                .toList();
        return new FormSchemaResponse(
                schema.getId(), schema.getName(), schema.getDescription(), schema.getVersion(),
                schema.getCreatedBy(), schema.getCreatedOn(), schema.getUpdatedOn(), fields);
    }
}
