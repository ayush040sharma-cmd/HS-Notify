package com.hs.notification.service.attachment;

import org.springframework.stereotype.Component;

/** Stub — same reasoning as SubscriberHistoryAttachmentProvider, for CDR (Call Detail Record) data. */
@Component
public class CdrSummaryAttachmentProvider implements AttachmentProvider {

    @Override
    public String key() { return "CDR_SUMMARY"; }

    @Override
    public String displayName() { return "CDR Summary"; }

    @Override
    public String description() { return "Call Detail Record summary export — usage-DB table/column mapping not yet confirmed."; }

    @Override
    public boolean isAvailable() { return false; }

    @Override
    public AttachmentResult generate(AttachmentContext context) {
        return AttachmentResult.failure(
                "CDR summary export requires a confirmed usage-DB table/column mapping — not yet configured");
    }
}
