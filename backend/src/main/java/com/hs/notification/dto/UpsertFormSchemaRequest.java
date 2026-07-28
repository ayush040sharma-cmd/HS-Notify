package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpsertFormSchemaRequest(
        @NotBlank String name,
        String description,
        List<FormFieldDto> fields
) {}
