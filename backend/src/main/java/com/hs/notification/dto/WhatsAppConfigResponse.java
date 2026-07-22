package com.hs.notification.dto;

import com.hs.notification.model.WhatsAppConfig;

public record WhatsAppConfigResponse(
        String businessAccountId,
        String phoneNumberId,
        String webhookUrl,
        boolean apiKeyConfigured
) {
    public static WhatsAppConfigResponse from(WhatsAppConfig c) {
        return new WhatsAppConfigResponse(
                c.getBusinessAccountId(), c.getPhoneNumberId(), c.getWebhookUrl(),
                c.getApiKey() != null && !c.getApiKey().isBlank());
    }
}
