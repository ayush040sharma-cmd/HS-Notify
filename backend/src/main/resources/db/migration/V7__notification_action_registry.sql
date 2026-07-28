-- Notification Action Registry: replaces the hardcoded scenario set that
-- previously lived in NotificationService.ALLOWED_CUSTOM_SCENARIOS.
--
-- Deliberately NOT tenant-scoped (no tenant_id column) — the prior hardcoded
-- set applied identically to every tenant, and this migration preserves that
-- exact behavior. Tenant-specific action catalogs can be layered on later
-- without breaking this shape (e.g. an optional tenant_id override table).
--
-- form_schema_id / attachment_schema_id are plain nullable BIGINT columns
-- with no FK constraint yet — the form_schema and attachment_schema tables
-- they'll eventually reference don't exist until later phases of the
-- notification-action-registry rollout. FK constraints get added in a
-- future migration once those tables land.
CREATE TABLE notification_action (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(60) NOT NULL,
    display_name          VARCHAR(150) NOT NULL,
    description           TEXT,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    approval_required     BOOLEAN NOT NULL DEFAULT FALSE,
    default_channel       VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    icon                  VARCHAR(60),
    display_order         INTEGER NOT NULL DEFAULT 0,
    role_required         VARCHAR(30),
    form_schema_id        BIGINT,
    attachment_schema_id  BIGINT,
    created_by            VARCHAR(100) NOT NULL DEFAULT 'system',
    created_on            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_action_code UNIQUE (code)
);

-- Seed the 5 scenarios that were previously hardcoded in
-- NotificationService.ALLOWED_CUSTOM_SCENARIOS, so every existing
-- /api/v1/notifications/send-custom caller keeps working unchanged.
INSERT INTO notification_action
    (code, display_name, description, enabled, approval_required, default_channel, display_order, created_by)
VALUES
    ('FRAUD_ALERT',    'Fraud Alert',          'Alert sent when suspicious subscriber activity is detected',        TRUE, FALSE, 'EMAIL', 10, 'system-migration'),
    ('ZERO_TOLERANCE', 'Zero Tolerance Alert', 'High-severity alert for zero-tolerance fraud policy violations',    TRUE, FALSE, 'EMAIL', 20, 'system-migration'),
    ('VENDOR_EMAIL',   'Vendor Notification',  'Notification sent to an external vendor or partner',                TRUE, FALSE, 'EMAIL', 30, 'system-migration'),
    ('CASE_SUMMARY',   'Case Summary',         'Summary of a case shared with stakeholders',                        TRUE, FALSE, 'EMAIL', 40, 'system-migration'),
    ('PR_CLOSE',       'PR Closure',           'Notification sent when a PR (Pattern Recognition) case is closed',  TRUE, FALSE, 'EMAIL', 50, 'system-migration');
