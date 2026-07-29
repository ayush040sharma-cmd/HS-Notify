package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpsertAttachmentSchemaRequest(
        @NotBlank String name,
        String description,
        List<String> providerKeys
) {}
