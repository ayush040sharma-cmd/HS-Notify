package com.hs.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** password is required on create; on update, blank/null leaves the existing password unchanged. */
public record UpsertUserRequest(
        @NotBlank String username,
        String displayName,
        String email,
        @NotBlank String role,
        String password,
        Boolean active
) {}
