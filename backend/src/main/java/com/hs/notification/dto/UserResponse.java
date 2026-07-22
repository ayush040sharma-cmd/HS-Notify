package com.hs.notification.dto;

import com.hs.notification.model.AppUser;

public record UserResponse(Long userId, String name, String email, String role, String status) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(
                u.getUserId(),
                u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                u.getUsername(),
                u.getRole(),
                u.isActive() ? "ACTIVE" : "INACTIVE");
    }
}
