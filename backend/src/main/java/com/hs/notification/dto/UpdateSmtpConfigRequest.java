package com.hs.notification.dto;

public record UpdateSmtpConfigRequest(
        String host,
        Integer port,
        String username,
        Boolean useTls,
        String fromName,
        String fromEmail,
        Integer maxPerMinute
) {}
