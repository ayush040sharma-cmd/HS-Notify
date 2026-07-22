package com.hs.notification.dto;

import com.hs.notification.model.SmtpConfig;

public record SmtpConfigResponse(
        String host,
        Integer port,
        String username,
        boolean useTls,
        String fromName,
        String fromEmail,
        Integer maxPerMinute
) {
    public static SmtpConfigResponse from(SmtpConfig c) {
        return new SmtpConfigResponse(
                c.getHost(), c.getPort(), c.getUsername(), c.isUseTls(),
                c.getFromDisplayName(), c.getFromAddress(), c.getMaxPerMinute());
    }
}
