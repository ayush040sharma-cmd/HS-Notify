-- Phase 3: unified POST /api/v1/notify engine.
--
-- channel: lets a ruleless (ad-hoc/direct) job declare its own dispatch
-- channel. Rule-based jobs are unaffected — MailDispatchService.resolveChannel
-- still checks rule.template.channel first; this column is only consulted
-- when there's no rule.
--
-- bcc_addresses: real BCC support, matching to_addresses/cc_addresses.
ALTER TABLE notification_job
    ADD COLUMN channel VARCHAR(20),
    ADD COLUMN bcc_addresses TEXT[];

-- /send-direct never had a scenario/action concept — it's a raw manual send
-- with no registry gate. Rather than silently bypassing the new unified
-- engine's action resolution, give it a real (enabled by default) action
-- code so it's now a governable capability like everything else, while
-- staying a no-op change for existing /send-direct callers, who never see
-- this code — it's used only internally when NotificationService.notify()
-- is called on their behalf.
INSERT INTO notification_action (code, display_name, description, enabled, approval_required, default_channel, display_order, created_by)
VALUES ('DIRECT_SEND', 'Direct Send', 'Raw manual send from the operational UI — internal action code used by the legacy /send-direct endpoint', TRUE, FALSE, 'EMAIL', 5, 'system-migration');
