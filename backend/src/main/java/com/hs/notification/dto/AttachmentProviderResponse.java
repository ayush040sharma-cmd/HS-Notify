package com.hs.notification.dto;

import com.hs.notification.service.attachment.AttachmentProvider;

public record AttachmentProviderResponse(String key, String displayName, String description, boolean available) {
    public static AttachmentProviderResponse from(AttachmentProvider provider) {
        return new AttachmentProviderResponse(provider.key(), provider.displayName(), provider.description(), provider.isAvailable());
    }
}
