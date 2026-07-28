-- Form Metadata Engine (Phase 2 of the metadata-driven platform rollout).
-- Lets each notification_action declare which fields its wizard should
-- render, instead of the frontend hardcoding a form per action.
CREATE TABLE form_schema (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    version       INTEGER NOT NULL DEFAULT 1,
    created_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    created_on    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_on    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- field_type supported values (validated at the application layer, not a DB
-- CHECK constraint, so the recognized set can grow without a migration):
-- TEXTBOX | TEXTAREA | DROPDOWN | CHECKBOX | RADIO | DATE | EMAIL |
-- FILE_UPLOAD | DYNAMIC_LOOKUP
CREATE TABLE form_fields (
    id                        BIGSERIAL PRIMARY KEY,
    form_schema_id            BIGINT NOT NULL REFERENCES form_schema(id) ON DELETE CASCADE,
    field_key                 VARCHAR(100) NOT NULL,
    label                     VARCHAR(150) NOT NULL,
    field_type                VARCHAR(30) NOT NULL,
    placeholder               VARCHAR(255),
    help_text                 VARCHAR(500),
    is_required               BOOLEAN NOT NULL DEFAULT FALSE,
    display_order             INTEGER NOT NULL DEFAULT 0,
    default_value             TEXT,
    -- DYNAMIC_LOOKUP: a named source the frontend resolves against a lookup
    -- API (e.g. "recipient-groups", "escalation-chains") — not a hardcoded
    -- endpoint per field.
    lookup_source             VARCHAR(255),
    -- Conditional Fields: show this field only when the field identified by
    -- conditional_on_field_key (within the same schema) equals
    -- conditional_on_value. Referenced by key rather than a self-FK so the
    -- frontend's visibility engine can stay generic (key/value comparison).
    conditional_on_field_key  VARCHAR(100),
    conditional_on_value      VARCHAR(255),
    CONSTRAINT uq_form_fields_schema_key UNIQUE (form_schema_id, field_key)
);

CREATE TABLE field_validation (
    id                BIGSERIAL PRIMARY KEY,
    form_field_id     BIGINT NOT NULL REFERENCES form_fields(id) ON DELETE CASCADE,
    validation_type   VARCHAR(30) NOT NULL,  -- REQUIRED | MIN_LENGTH | MAX_LENGTH | PATTERN | MIN | MAX | EMAIL_FORMAT
    validation_value  VARCHAR(255),
    error_message     VARCHAR(255)
);

CREATE TABLE field_options (
    id             BIGSERIAL PRIMARY KEY,
    form_field_id  BIGINT NOT NULL REFERENCES form_fields(id) ON DELETE CASCADE,
    option_value   VARCHAR(255) NOT NULL,
    option_label   VARCHAR(255) NOT NULL,
    display_order  INTEGER NOT NULL DEFAULT 0
);

-- Wire the FK left as a placeholder in V7 now that form_schema exists.
ALTER TABLE notification_action
    ADD CONSTRAINT fk_notification_action_form_schema
    FOREIGN KEY (form_schema_id) REFERENCES form_schema(id);

-- Demonstrative example matching the Fraud Alert form from the platform
-- spec, linked to the already-seeded FRAUD_ALERT action so
-- GET /api/v1/actions/FRAUD_ALERT/schema returns something real out of the box.
INSERT INTO form_schema (id, name, description, created_by) VALUES
    (1, 'Fraud Alert Form', 'Dynamic form for the Fraud Alert notification action', 'system-migration');

-- Explicit-id inserts into a BIGSERIAL/IDENTITY column don't advance the
-- underlying sequence — without this, the next auto-generated insert would
-- also try id=1 and collide.
SELECT setval(pg_get_serial_sequence('form_schema', 'id'), (SELECT MAX(id) FROM form_schema));

INSERT INTO form_fields
    (form_schema_id, field_key, label, field_type, placeholder, help_text, is_required, display_order, lookup_source)
VALUES
    (1, 'to_address',      'To Address',        'EMAIL',    'analyst@subex.com',  NULL, TRUE,  10, NULL),
    (1, 'cc_address',      'CC Address',        'EMAIL',    NULL,                 NULL, FALSE, 20, NULL),
    (1, 'bcc_address',     'BCC Address',       'EMAIL',    NULL,                 NULL, FALSE, 30, NULL),
    (1, 'subject',         'Subject',           'TEXTBOX',  'Fraud Alert - Case', NULL, TRUE,  40, NULL),
    (1, 'priority',        'Priority',          'DROPDOWN', NULL,                 NULL, TRUE,  50, NULL),
    (1, 'severity',        'Severity',          'DROPDOWN', NULL,                 NULL, TRUE,  60, NULL),
    (1, 'custom_message',  'Custom Message',    'TEXTAREA', 'Add any additional context', NULL, FALSE, 70, NULL),
    (1, 'include_case_details', 'Include Case Details', 'CHECKBOX', NULL, NULL, FALSE, 80, NULL),
    (1, 'include_pr_records',   'Include PR Records',   'CHECKBOX', NULL, 'Attaches system_pr_results CSV for this case', FALSE, 90, NULL),
    (1, 'recipient_group', 'Recipient Group',   'DYNAMIC_LOOKUP', NULL, NULL, FALSE, 100, 'recipient-groups');

INSERT INTO field_validation (form_field_id, validation_type, validation_value, error_message)
SELECT id, 'EMAIL_FORMAT', NULL, 'Must be a valid email address' FROM form_fields WHERE form_schema_id = 1 AND field_key = 'to_address';
INSERT INTO field_validation (form_field_id, validation_type, validation_value, error_message)
SELECT id, 'MAX_LENGTH', '200', 'Subject must be under 200 characters' FROM form_fields WHERE form_schema_id = 1 AND field_key = 'subject';

INSERT INTO field_options (form_field_id, option_value, option_label, display_order)
SELECT id, 'LOW', 'Low', 10 FROM form_fields WHERE form_schema_id = 1 AND field_key = 'priority'
UNION ALL
SELECT id, 'MEDIUM', 'Medium', 20 FROM form_fields WHERE form_schema_id = 1 AND field_key = 'priority'
UNION ALL
SELECT id, 'HIGH', 'High', 30 FROM form_fields WHERE form_schema_id = 1 AND field_key = 'priority'
UNION ALL
SELECT id, 'CRITICAL', 'Critical', 40 FROM form_fields WHERE form_schema_id = 1 AND field_key = 'priority';

INSERT INTO field_options (form_field_id, option_value, option_label, display_order)
SELECT id, 'LOW', 'Low', 10 FROM form_fields WHERE form_schema_id = 1 AND field_key = 'severity'
UNION ALL
SELECT id, 'MEDIUM', 'Medium', 20 FROM form_fields WHERE form_schema_id = 1 AND field_key = 'severity'
UNION ALL
SELECT id, 'HIGH', 'High', 30 FROM form_fields WHERE form_schema_id = 1 AND field_key = 'severity';

UPDATE notification_action SET form_schema_id = 1 WHERE code = 'FRAUD_ALERT';
