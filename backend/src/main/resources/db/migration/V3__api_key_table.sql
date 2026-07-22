-- API key table: replaces the env-var api-key map (Milestone 2).
-- Keys are stored as BCrypt hashes; the first 8 chars of the raw key are
-- kept in plain text (key_prefix) so the resolver can narrow candidates
-- before doing the BCrypt comparison without scanning every row.

CREATE TABLE api_key (
    key_id        BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenant(tenant_id),
    key_prefix    VARCHAR(16)  NOT NULL,
    key_hash      VARCHAR(256) NOT NULL UNIQUE,
    description   VARCHAR(200),
    created_by    VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ,
    is_revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at    TIMESTAMPTZ,
    revoked_by    VARCHAR(100)
);

CREATE INDEX idx_api_key_prefix ON api_key (key_prefix) WHERE is_revoked = FALSE;
