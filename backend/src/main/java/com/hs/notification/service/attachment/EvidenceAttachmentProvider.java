package com.hs.notification.service.attachment;

import org.springframework.stereotype.Component;

/**
 * Stub — case-evidence files (screenshots, documents an analyst attached
 * during investigation) live in HyperSense case management, and no schema
 * for that data source has been confirmed yet. Registered now so the
 * provider is a real, governable capability the moment that's wired,
 * rather than something invented without a real backing table.
 */
@Component
public class EvidenceAttachmentProvider implements AttachmentProvider {

    @Override
    public String key() { return "EVIDENCE"; }

    @Override
    public String displayName() { return "Case Evidence Files"; }

    @Override
    public String description() { return "Evidence files attached to a case in HyperSense case management — data source not yet confirmed."; }

    @Override
    public boolean isAvailable() { return false; }

    @Override
    public AttachmentResult generate(AttachmentContext context) {
        return AttachmentResult.failure(
                "Evidence-file attachment requires a confirmed case-evidence data source from HyperSense case management — not yet integrated");
    }
}
