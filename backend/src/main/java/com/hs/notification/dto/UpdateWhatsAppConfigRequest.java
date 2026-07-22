package com.hs.notification.dto;

public record UpdateWhatsAppConfigRequest(
        String businessAccountId,
        String phoneNumberId,
        String webhookUrl,
        String apiKey
) {}
