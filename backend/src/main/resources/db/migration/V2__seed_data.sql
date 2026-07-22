-- Seed data: a default tenant (Zain) with a fully working rule end-to-end
-- so the platform is never "0% / empty" on first run.

INSERT INTO tenant (tenant_code, tenant_name) VALUES ('ZAIN', 'Zain Group');
INSERT INTO tenant (tenant_code, tenant_name) VALUES ('DEFAULT', 'Default / Internal');

-- Feature toggles
INSERT INTO feature_toggle (tenant_id, feature_key, scope_type, is_enabled)
SELECT tenant_id, 'NOTIFICATIONS_ENABLED', 'TENANT', TRUE FROM tenant WHERE tenant_code = 'ZAIN';
INSERT INTO feature_toggle (tenant_id, feature_key, scope_type, is_enabled)
SELECT tenant_id, 'ATTACHMENTS_ENABLED', 'TENANT', TRUE FROM tenant WHERE tenant_code = 'ZAIN';

-- SMTP config (secret_ref points to a vault key - never a plaintext password)
INSERT INTO smtp_config (tenant_id, host, port, use_tls, username, secret_ref, from_address, from_display_name, max_per_minute)
SELECT tenant_id, 'smtp.zain-internal.example.com', 587, TRUE, 'hs-notify-svc',
       'vault://hs/smtp/zain/svc-password', 'fraud-ops-noreply@zain.example.com',
       'Zain Fraud Operations', 120
FROM tenant WHERE tenant_code = 'ZAIN';

-- Recipient groups
INSERT INTO recipient_group (tenant_id, group_code, description)
SELECT tenant_id, 'FRAUD_OPS_TEAM', 'Primary fraud operations distribution list' FROM tenant WHERE tenant_code = 'ZAIN';

INSERT INTO recipient_group_member (recipient_group_id, recipient_type, email_address)
SELECT recipient_group_id, 'TO', 'fraud-ops@zain.example.com'
FROM recipient_group WHERE group_code = 'FRAUD_OPS_TEAM';

INSERT INTO recipient_group_member (recipient_group_id, recipient_type, email_address)
SELECT recipient_group_id, 'CC', 'fraud-ops-lead@zain.example.com'
FROM recipient_group WHERE group_code = 'FRAUD_OPS_TEAM';

-- Escalation chain
INSERT INTO escalation_chain (tenant_id, chain_code, description)
SELECT tenant_id, 'L1_L2_SUPPORT', 'Standard two-tier support escalation' FROM tenant WHERE tenant_code = 'ZAIN';

INSERT INTO escalation_chain_step (escalation_chain_id, step_order, recipient_email, delay_minutes)
SELECT escalation_chain_id, 1, 'support-l1@hs-vendor.example.com', 0
FROM escalation_chain WHERE chain_code = 'L1_L2_SUPPORT';

INSERT INTO escalation_chain_step (escalation_chain_id, step_order, recipient_email, delay_minutes)
SELECT escalation_chain_id, 2, 'support-l2@hs-vendor.example.com', 15
FROM escalation_chain WHERE chain_code = 'L1_L2_SUPPORT';

-- Attachment rule
INSERT INTO attachment_rule (tenant_id, rule_code, attachment_source, report_identifier, output_format, on_generation_failure)
SELECT tenant_id, 'PR_CLOSE_REPORT', 'REPORT_SERVICE', 'PR_CLOSURE_SUMMARY', 'PDF', 'SEND_WITHOUT_ATTACHMENT'
FROM tenant WHERE tenant_code = 'ZAIN';

-- Template (already approved + active, so the demo rule can be ACTIVE too)
INSERT INTO notification_template
  (tenant_id, template_code, version, subject_template, body_template, allowed_variables, pii_mask_fields, status, created_by, approved_by, approved_at)
SELECT tenant_id, 'PR_CLOSE_MAIL', 1,
  'PR {{pr_id}} Closed - Action Required',
  '<p>Dear {{account_name}} team,</p><p>PR <strong>{{pr_id}}</strong> was closed by {{closed_by}} on {{close_date}}.</p><p>Please review the attached summary.</p>',
  ARRAY['pr_id','account_name','closed_by','close_date'],
  ARRAY[]::text[],
  'ACTIVE', 'system-seed', 'system-seed', now()
FROM tenant WHERE tenant_code = 'ZAIN';

INSERT INTO notification_template
  (tenant_id, template_code, version, subject_template, body_template, allowed_variables, pii_mask_fields, status, created_by)
SELECT tenant_id, 'CASE_ESCALATE_MAIL', 1,
  'Case {{case_id}} Escalated - {{severity}}',
  '<p>Case <strong>{{case_id}}</strong> has been escalated to {{escalation_level}}.</p><p>Reason: {{reason}}</p>',
  ARRAY['case_id','severity','escalation_level','reason'],
  ARRAY['account_number'],
  'PENDING_REVIEW', 'system-seed'
FROM tenant WHERE tenant_code = 'ZAIN';

-- Rule 1: active, fully wired end to end
INSERT INTO notification_rule
  (tenant_id, rule_code, trigger_event, trigger_source, template_id, recipient_group_id, attachment_rule_id,
   escalation_chain_id, max_retry_count, retry_backoff_seconds, on_final_failure, status, is_active, created_by, approved_by, approved_at)
SELECT
  t.tenant_id, 'PR_CLOSE_RULE', 'PR_CLOSED', 'ALARM_CLOSURE',
  tmpl.template_id, rg.recipient_group_id, ar.attachment_rule_id, ec.escalation_chain_id,
  3, 60, 'ESCALATE', 'ACTIVE', TRUE, 'system-seed', 'system-seed', now()
FROM tenant t
JOIN notification_template tmpl ON tmpl.tenant_id = t.tenant_id AND tmpl.template_code = 'PR_CLOSE_MAIL'
JOIN recipient_group rg ON rg.tenant_id = t.tenant_id AND rg.group_code = 'FRAUD_OPS_TEAM'
JOIN attachment_rule ar ON ar.tenant_id = t.tenant_id AND ar.rule_code = 'PR_CLOSE_REPORT'
JOIN escalation_chain ec ON ec.tenant_id = t.tenant_id AND ec.chain_code = 'L1_L2_SUPPORT'
WHERE t.tenant_code = 'ZAIN';

-- Rule 2: pending review (maker-checker demo)
INSERT INTO notification_rule
  (tenant_id, rule_code, trigger_event, trigger_source, template_id, recipient_group_id,
   max_retry_count, retry_backoff_seconds, on_final_failure, status, is_active, created_by)
SELECT
  t.tenant_id, 'CASE_ESCALATE_RULE', 'CASE_ESCALATED', 'CASE_CLOSURE',
  tmpl.template_id, rg.recipient_group_id,
  3, 120, 'ESCALATE', 'PENDING_REVIEW', FALSE, 'analyst.jdoe'
FROM tenant t
JOIN notification_template tmpl ON tmpl.tenant_id = t.tenant_id AND tmpl.template_code = 'CASE_ESCALATE_MAIL'
JOIN recipient_group rg ON rg.tenant_id = t.tenant_id AND rg.group_code = 'FRAUD_OPS_TEAM'
WHERE t.tenant_code = 'ZAIN';

-- Watchdog defaults
INSERT INTO watchdog_config (poll_interval_seconds, fail_threshold, escalation_recipients, heartbeat_enabled)
VALUES (30, 3, ARRAY['support-l1@hs-vendor.example.com'], TRUE);

INSERT INTO watchdog_state (consecutive_failures, total_restarts, escalations_sent, last_up_at)
VALUES (0, 0, 0, now());

-- A couple of historical jobs + audit rows so dashboard isn't empty on first load
INSERT INTO notification_job
  (tenant_id, rule_id, idempotency_key, source_reference, source_type, to_addresses, cc_addresses,
   subject, rendered_body, context_json, attachment_status, status, attempt_count, sent_at)
SELECT
  t.tenant_id, r.rule_id, 'seed-job-001', 'PR-10293', 'PR',
  ARRAY['fraud-ops@zain.example.com'], ARRAY['fraud-ops-lead@zain.example.com'],
  'PR 10293 Closed - Action Required',
  '<p>Dear Zain team, PR 10293 was closed by jane.analyst on 2026-06-20.</p>',
  '{"pr_id":"10293","account_name":"Zain","closed_by":"jane.analyst","close_date":"2026-06-20"}'::jsonb,
  'GENERATED', 'SENT', 1, now() - interval '2 hours'
FROM tenant t JOIN notification_rule r ON r.tenant_id = t.tenant_id AND r.rule_code = 'PR_CLOSE_RULE'
WHERE t.tenant_code = 'ZAIN';

INSERT INTO notification_audit_log (tenant_id, job_id, rule_id, event_type, event_detail, actor, response_time_ms, occurred_at)
SELECT j.tenant_id, j.job_id, j.rule_id, 'SEND_SUCCESS', 'Email delivered via SMTP relay', 'system', 842, j.sent_at
FROM notification_job j WHERE j.idempotency_key = 'seed-job-001';

INSERT INTO watchdog_health_log (status, response_time_ms, detail, consecutive_failures, action_taken)
VALUES ('UP', 38, 'Health endpoint responded 200 OK', 0, 'NONE');

-- Default admin user
INSERT INTO app_user (username, display_name, role, tenant_id)
SELECT 'admin', 'Platform Administrator', 'ADMIN', NULL;
