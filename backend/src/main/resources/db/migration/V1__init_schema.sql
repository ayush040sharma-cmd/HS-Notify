-- =====================================================================
-- HS Notification Platform - Core Schema
-- Postgres. Designed to be portable to Oracle with minor type changes
-- (SERIAL -> sequence, JSONB -> CLOB+check, etc.) - kept vendor-light.
-- =====================================================================

-- ---------------------------------------------------------------------
-- TENANTS  (multi-tenancy: every customer, e.g. Zain, is a tenant)
-- ---------------------------------------------------------------------
CREATE TABLE tenant (
    tenant_id         BIGSERIAL PRIMARY KEY,
    tenant_code       VARCHAR(50)  NOT NULL UNIQUE,         -- e.g. 'ZAIN'
    tenant_name       VARCHAR(200) NOT NULL,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- FEATURE TOGGLES  (enablement per customer / alarm / action)
-- ---------------------------------------------------------------------
CREATE TABLE feature_toggle (
    toggle_id         BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT REFERENCES tenant(tenant_id),  -- NULL = global default
    feature_key       VARCHAR(100) NOT NULL,                -- e.g. 'NOTIFICATIONS_ENABLED', 'ATTACHMENTS_ENABLED'
    scope_type        VARCHAR(30)  NOT NULL DEFAULT 'TENANT', -- TENANT | ALARM_TYPE | ACTION_TYPE
    scope_value       VARCHAR(100),                          -- e.g. alarm type code, action code
    is_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by        VARCHAR(100),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, feature_key, scope_type, scope_value)
);

-- ---------------------------------------------------------------------
-- SMTP CONFIG  (per tenant SMTP settings; secrets referenced, not stored)
-- ---------------------------------------------------------------------
CREATE TABLE smtp_config (
    smtp_config_id    BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT NOT NULL REFERENCES tenant(tenant_id),
    host              VARCHAR(200) NOT NULL,
    port              INTEGER      NOT NULL DEFAULT 587,
    use_tls           BOOLEAN      NOT NULL DEFAULT TRUE,
    username           VARCHAR(200),
    secret_ref        VARCHAR(200) NOT NULL,   -- pointer into HS secrets vault, never plaintext password
    from_address       VARCHAR(200) NOT NULL,
    from_display_name VARCHAR(200),
    max_per_minute    INTEGER      NOT NULL DEFAULT 60,    -- throttling
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, is_active)
);

-- ---------------------------------------------------------------------
-- TEMPLATES  (versioned: draft -> review -> approved -> active)
-- ---------------------------------------------------------------------
CREATE TABLE notification_template (
    template_id       BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT NOT NULL REFERENCES tenant(tenant_id),
    template_code     VARCHAR(100) NOT NULL,        -- e.g. 'PR_CLOSE_MAIL'
    version           INTEGER      NOT NULL DEFAULT 1,
    subject_template  VARCHAR(500) NOT NULL,        -- e.g. 'PR {{pr_id}} Closed - Action Required'
    body_template      TEXT         NOT NULL,         -- HTML, mustache-style {{var}} placeholders
    allowed_variables TEXT[]       NOT NULL DEFAULT '{}',  -- whitelist for DLP - only these may be interpolated
    pii_mask_fields    TEXT[]       NOT NULL DEFAULT '{}', -- which variables get masked (e.g. account_number)
    channel           VARCHAR(20)  NOT NULL DEFAULT 'EMAIL', -- EMAIL | SMS | WEBHOOK | SLACK (future channels)
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT', -- DRAFT | PENDING_REVIEW | APPROVED | ACTIVE | RETIRED
    created_by        VARCHAR(100) NOT NULL,
    approved_by       VARCHAR(100),
    approved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, template_code, version)
);

CREATE INDEX idx_template_active ON notification_template(tenant_id, template_code) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- ATTACHMENT RULES  (what report to generate/attach, and how)
-- ---------------------------------------------------------------------
CREATE TABLE attachment_rule (
    attachment_rule_id BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(tenant_id),
    rule_code           VARCHAR(100) NOT NULL,
    attachment_source   VARCHAR(50)  NOT NULL,   -- REPORT_SERVICE | STATIC_FILE | GENERATED_PDF | NONE
    report_identifier   VARCHAR(200),             -- which report/query to call if REPORT_SERVICE
    output_format       VARCHAR(10)  NOT NULL DEFAULT 'PDF',  -- PDF | CSV | XLSX
    max_size_mb         INTEGER      NOT NULL DEFAULT 10,
    on_generation_failure VARCHAR(30) NOT NULL DEFAULT 'SEND_WITHOUT_ATTACHMENT', -- SEND_WITHOUT_ATTACHMENT | HOLD_JOB | FAIL_JOB
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, rule_code)
);

-- ---------------------------------------------------------------------
-- RECIPIENT GROUPS / ROUTING  (who receives what)
-- ---------------------------------------------------------------------
CREATE TABLE recipient_group (
    recipient_group_id BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(tenant_id),
    group_code          VARCHAR(100) NOT NULL,
    description          VARCHAR(300),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, group_code)
);

CREATE TABLE recipient_group_member (
    member_id           BIGSERIAL PRIMARY KEY,
    recipient_group_id  BIGINT NOT NULL REFERENCES recipient_group(recipient_group_id) ON DELETE CASCADE,
    recipient_type       VARCHAR(20) NOT NULL DEFAULT 'TO',  -- TO | CC | BCC
    email_address        VARCHAR(200),                        -- static email OR...
    hs_user_ref           VARCHAR(100),                        -- ...resolve dynamically from HS user/role table
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- ESCALATION CHAINS  (support escalation recipients, ordered)
-- ---------------------------------------------------------------------
CREATE TABLE escalation_chain (
    escalation_chain_id BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenant(tenant_id),
    chain_code            VARCHAR(100) NOT NULL,
    description            VARCHAR(300),
    UNIQUE (tenant_id, chain_code)
);

CREATE TABLE escalation_chain_step (
    step_id               BIGSERIAL PRIMARY KEY,
    escalation_chain_id   BIGINT NOT NULL REFERENCES escalation_chain(escalation_chain_id) ON DELETE CASCADE,
    step_order             INTEGER NOT NULL,
    recipient_email        VARCHAR(200) NOT NULL,
    delay_minutes           INTEGER NOT NULL DEFAULT 0,    -- wait this long after previous step before notifying
    UNIQUE (escalation_chain_id, step_order)
);

-- ---------------------------------------------------------------------
-- NOTIFICATION RULES  (the core "when / who / what / attachment / retry" config)
-- Lifecycle: DRAFT -> PENDING_REVIEW -> APPROVED -> ACTIVE  (maker-checker)
-- ---------------------------------------------------------------------
CREATE TABLE notification_rule (
    rule_id               BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT NOT NULL REFERENCES tenant(tenant_id),
    rule_code               VARCHAR(100) NOT NULL,
    trigger_event           VARCHAR(100) NOT NULL,          -- e.g. PR_CLOSED, CASE_ESCALATED
    trigger_source           VARCHAR(30)  NOT NULL DEFAULT 'ALARM_CLOSURE', -- ALARM_CLOSURE | CASE_CLOSURE | MANUAL | BMS | PAS
    template_id              BIGINT NOT NULL REFERENCES notification_template(template_id),
    recipient_group_id        BIGINT REFERENCES recipient_group(recipient_group_id),
    attachment_rule_id        BIGINT REFERENCES attachment_rule(attachment_rule_id),  -- NULL = no attachment
    escalation_chain_id        BIGINT REFERENCES escalation_chain(escalation_chain_id),

    -- retry / failure policy (per rule, not just global watchdog)
    max_retry_count             INTEGER NOT NULL DEFAULT 3,
    retry_backoff_seconds        INTEGER NOT NULL DEFAULT 60,
    retry_backoff_multiplier     NUMERIC(4,2) NOT NULL DEFAULT 2.0,
    on_final_failure              VARCHAR(30) NOT NULL DEFAULT 'ESCALATE', -- ESCALATE | DROP | HOLD_FOR_MANUAL

    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT|PENDING_REVIEW|APPROVED|ACTIVE|DISABLED
    is_active                 BOOLEAN     NOT NULL DEFAULT FALSE,    -- only ACTIVE+is_active rules fire
    created_by                VARCHAR(100) NOT NULL,
    approved_by                VARCHAR(100),
    approved_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, rule_code)
);

CREATE INDEX idx_rule_trigger ON notification_rule(tenant_id, trigger_event) WHERE is_active = TRUE AND status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- NOTIFICATION JOBS  (one row per send attempt request - the queue)
-- ---------------------------------------------------------------------
CREATE TABLE notification_job (
    job_id                  BIGSERIAL PRIMARY KEY,
    tenant_id                BIGINT NOT NULL REFERENCES tenant(tenant_id),
    rule_id                   BIGINT REFERENCES notification_rule(rule_id),   -- NULL for direct/manual sends
    idempotency_key            VARCHAR(200),    -- prevents duplicate sends from retried HS workflow calls
    source_reference            VARCHAR(100),    -- alarm id / case id / PR id that triggered this
    source_type                  VARCHAR(30),      -- ALARM | CASE | PR | MANUAL
    to_addresses                 TEXT[]   NOT NULL DEFAULT '{}',
    cc_addresses                  TEXT[]   NOT NULL DEFAULT '{}',
    subject                        VARCHAR(500),
    rendered_body                   TEXT,
    context_json                     JSONB,            -- the variables used to render the template
    attachment_status                VARCHAR(30) DEFAULT 'NOT_APPLICABLE', -- NOT_APPLICABLE|PENDING|GENERATED|FAILED
    attachment_path                   VARCHAR(500),

    status                            VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING|SENDING|SENT|FAILED|RETRYING|ESCALATED|CANCELLED
    attempt_count                      INTEGER NOT NULL DEFAULT 0,
    max_retry_count                    INTEGER NOT NULL DEFAULT 3,
    next_retry_at                       TIMESTAMPTZ,
    last_error                          TEXT,

    delivery_confirmed                  BOOLEAN DEFAULT NULL,  -- NULL=unknown, TRUE=bounced-clean, FALSE=bounced
    bounce_reason                        TEXT,

    created_at                           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                            TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at                                TIMESTAMPTZ,

    UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_job_status ON notification_job(status, next_retry_at);
CREATE INDEX idx_job_tenant_created ON notification_job(tenant_id, created_at DESC);
CREATE INDEX idx_job_source ON notification_job(source_type, source_reference);

-- ---------------------------------------------------------------------
-- AUDIT LOG  (immutable, append-only - for compliance / SRI-style dashboard)
-- ---------------------------------------------------------------------
CREATE TABLE notification_audit_log (
    audit_id          BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(tenant_id),
    job_id                BIGINT REFERENCES notification_job(job_id),
    rule_id                BIGINT REFERENCES notification_rule(rule_id),
    event_type              VARCHAR(50) NOT NULL,   -- JOB_CREATED|SEND_ATTEMPT|SEND_SUCCESS|SEND_FAILED|RETRY_SCHEDULED|ESCALATED|RULE_CHANGED|TEMPLATE_CHANGED
    event_detail              TEXT,
    actor                       VARCHAR(100),            -- system or username, for rule/template changes
    response_time_ms           INTEGER,
    occurred_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_tenant_time ON notification_audit_log(tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_job ON notification_audit_log(job_id);

-- ---------------------------------------------------------------------
-- WATCHDOG HEALTH HISTORY
-- ---------------------------------------------------------------------
CREATE TABLE watchdog_health_log (
    health_log_id       BIGSERIAL PRIMARY KEY,
    checked_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                  VARCHAR(20) NOT NULL,   -- UP | DOWN | DEGRADED
    response_time_ms        INTEGER,
    detail                   TEXT,
    consecutive_failures      INTEGER NOT NULL DEFAULT 0,
    action_taken              VARCHAR(50)             -- NONE | RESTART_ATTEMPTED | ESCALATED
);

CREATE INDEX idx_watchdog_time ON watchdog_health_log(checked_at DESC);

CREATE TABLE watchdog_config (
    watchdog_config_id   BIGSERIAL PRIMARY KEY,
    poll_interval_seconds  INTEGER NOT NULL DEFAULT 30,
    fail_threshold           INTEGER NOT NULL DEFAULT 3,
    restart_command          VARCHAR(500),               -- how the watchdog attempts auto-recovery
    escalation_recipients      TEXT[] NOT NULL DEFAULT '{}',
    heartbeat_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    heartbeat_interval_minutes    INTEGER NOT NULL DEFAULT 60,
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE watchdog_state (
    watchdog_state_id   BIGSERIAL PRIMARY KEY,
    consecutive_failures   INTEGER NOT NULL DEFAULT 0,
    total_restarts            INTEGER NOT NULL DEFAULT 0,
    escalations_sent           INTEGER NOT NULL DEFAULT 0,
    last_up_at                  TIMESTAMPTZ,
    last_down_at                  TIMESTAMPTZ,
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- RATE LIMIT TRACKING  (per tenant, per rule, per minute window)
-- ---------------------------------------------------------------------
CREATE TABLE rate_limit_bucket (
    bucket_id        BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenant(tenant_id),
    rule_id              BIGINT REFERENCES notification_rule(rule_id),
    window_start           TIMESTAMPTZ NOT NULL,
    send_count               INTEGER NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, rule_id, window_start)
);

-- ---------------------------------------------------------------------
-- USERS (lightweight - for who-approved-what; HS likely has its own
-- user/auth system, this is a reference table populated via SSO claims)
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    user_id            BIGSERIAL PRIMARY KEY,
    username             VARCHAR(100) NOT NULL UNIQUE,
    display_name           VARCHAR(200),
    role                     VARCHAR(30) NOT NULL DEFAULT 'VIEWER', -- VIEWER | OPERATOR | APPROVER | ADMIN
    tenant_id                 BIGINT REFERENCES tenant(tenant_id),    -- NULL = cross-tenant (HS support staff)
    is_active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);
