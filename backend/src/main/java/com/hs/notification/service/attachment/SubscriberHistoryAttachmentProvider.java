package com.hs.notification.service.attachment;

import org.springframework.stereotype.Component;

/**
 * Stub — subscriber-history data likely lives in the same usage DB as
 * system_pr_results_*, but the real table/column mapping hasn't been
 * confirmed (same category of gap as CaseWatchScheduler's case_owner_email
 * TODO). Once confirmed, this can reuse PrRecordsExportService's
 * CopyManager-based pattern directly.
 */
@Component
public class SubscriberHistoryAttachmentProvider implements AttachmentProvider {

    @Override
    public String key() { return "SUBSCRIBER_HISTORY"; }

    @Override
    public String displayName() { return "Subscriber History"; }

    @Override
    public String description() { return "Subscriber usage/event history export — usage-DB table/column mapping not yet confirmed."; }

    @Override
    public boolean isAvailable() { return false; }

    @Override
    public AttachmentResult generate(AttachmentContext context) {
        return AttachmentResult.failure(
                "Subscriber history export requires a confirmed usage-DB table/column mapping — not yet configured");
    }
}
