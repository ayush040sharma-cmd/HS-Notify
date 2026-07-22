-- ---------------------------------------------------------------------
-- WHATSAPP CONFIG  (per-tenant WhatsApp Business API settings)
-- ---------------------------------------------------------------------
-- Lets an admin fill in the WhatsApp Business API details ahead of the
-- real integration being wired up. WhatsAppChannelSender remains a stub
-- and does not read this table yet — it exists purely so nothing needs
-- re-entering once real sending is implemented.
--
-- api_key is plaintext for now — there is no secrets-vault integration
-- anywhere in this codebase yet (smtp_config.secret_ref has the same gap,
-- and is in fact never populated with a real secret at all). Revisit
-- before this channel goes live.
CREATE TABLE whatsapp_config (
    whatsapp_config_id   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenant(tenant_id),
    business_account_id  VARCHAR(200),
    phone_number_id      VARCHAR(200),
    webhook_url          VARCHAR(500),
    api_key              VARCHAR(500),
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, is_active)
);
