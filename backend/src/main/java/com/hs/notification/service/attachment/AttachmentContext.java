package com.hs.notification.service.attachment;

import com.hs.notification.model.Tenant;

import java.util.Map;

/** Everything a provider needs — deliberately the same context/payload map every other send path already threads through. */
public record AttachmentContext(Tenant tenant, Map<String, Object> payload) {}
