package com.hs.notification.service.attachment;

/**
 * Strategy interface for a pluggable attachment source (Phase 4 of the
 * metadata-driven platform rollout) — replaces the single hardcoded
 * "if(includePrRecords) generate CSV" branch with a registry of independent
 * providers. Mirrors the existing ChannelSender pattern (see
 * MailDispatchService: auto-collected via Spring, keyed by a string) rather
 * than introducing a different style for the same kind of problem.
 *
 * Implementations must never throw out of generate() — every failure mode
 * (not configured, missing input, unreachable dependency) is a
 * AttachmentResult.failure(reason), so one provider failing never blocks a
 * send or a sibling provider in the same request.
 */
public interface AttachmentProvider {

    /** Stable machine key, e.g. "PR_RECORDS" — what callers reference in attachmentOptions.providers. */
    String key();

    String displayName();

    String description();

    /** Whether this provider is currently usable (configured), without attempting real generation. */
    boolean isAvailable();

    AttachmentResult generate(AttachmentContext context);
}
