-- Attachment Provider Pattern (Phase 4 of the metadata-driven platform
-- rollout). attachment_schema is deliberately much simpler than form_schema
-- — just a named, ordered list of provider keys an action can offer,
-- since providers (unlike form fields) have no nested validation/options
-- of their own; the provider registry (GET /api/v1/attachments/providers)
-- is the source of truth for what a key means.
CREATE TABLE attachment_schema (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    created_by    VARCHAR(100) NOT NULL DEFAULT 'system',
    created_on    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attachment_schema_provider (
    id                     BIGSERIAL PRIMARY KEY,
    attachment_schema_id   BIGINT NOT NULL REFERENCES attachment_schema(id) ON DELETE CASCADE,
    provider_key           VARCHAR(60) NOT NULL,
    display_order          INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_attachment_schema_provider UNIQUE (attachment_schema_id, provider_key)
);

-- Wires the FK left as a placeholder in V7 now that attachment_schema exists.
ALTER TABLE notification_action
    ADD CONSTRAINT fk_notification_action_attachment_schema
    FOREIGN KEY (attachment_schema_id) REFERENCES attachment_schema(id);

-- Demonstrative example: PR_CLOSE can offer both PR-records export formats.
INSERT INTO attachment_schema (id, name, description, created_by) VALUES
    (1, 'PR Records Bundle', 'PR records export, CSV and Excel', 'system-migration');
INSERT INTO attachment_schema_provider (attachment_schema_id, provider_key, display_order) VALUES
    (1, 'PR_RECORDS', 10),
    (1, 'EXCEL_EXPORT', 20);
SELECT setval(pg_get_serial_sequence('attachment_schema', 'id'), (SELECT MAX(id) FROM attachment_schema));

UPDATE notification_action SET attachment_schema_id = 1 WHERE code = 'PR_CLOSE';
