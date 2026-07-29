package com.hs.notification.dto;

import com.hs.notification.model.AppUser;

public record UserResponse(Long userId, String username, String name, String email, String role, String status) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(
                u.getUserId(),
                u.getUsername(),
                u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                u.getEmail(),
                u.getRole(),
                u.isActive() ? "ACTIVE" : "INACTIVE");
    }
}
