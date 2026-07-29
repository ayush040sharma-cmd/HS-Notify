package com.hs.notification.service.attachment;

import org.springframework.stereotype.Component;

/**
 * Stub, not a fake implementation — see Phase 6 of the platform rollout
 * (a standalone Playwright/Superset screenshot microservice; this
 * intentionally does NOT embed browser-automation logic inside this
 * Spring Boot app). Registered now so the provider registry/UI can already
 * list it and callers get a clear, honest failure instead of a 404.
 */
@Component
public class DashboardSnapshotAttachmentProvider implements AttachmentProvider {

    @Override
    public String key() { return "DASHBOARD_SNAPSHOT"; }

    @Override
    public String displayName() { return "Dashboard Snapshot"; }

    @Override
    public String description() { return "Superset dashboard screenshot, delivered by a separate microservice — not yet built (Phase 6)."; }

    @Override
    public boolean isAvailable() { return false; }

    @Override
    public AttachmentResult generate(AttachmentContext context) {
        return AttachmentResult.failure(
                "Dashboard snapshot generation requires the Superset screenshot microservice (Phase 6), not yet built");
    }
}
