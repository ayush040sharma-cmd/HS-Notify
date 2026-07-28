-- New tenant for the real Subex-side integration (case-watch poller etc.),
-- created fresh alongside ZAIN/DEFAULT rather than renaming ZAIN — ZAIN
-- keeps its existing rules/templates/API keys/history untouched.
INSERT INTO tenant (tenant_code, tenant_name) VALUES ('SUBEX', 'Subex');

INSERT INTO feature_toggle (tenant_id, feature_key, scope_type, is_enabled)
SELECT tenant_id, 'NOTIFICATIONS_ENABLED', 'TENANT', TRUE FROM tenant WHERE tenant_code = 'SUBEX';
INSERT INTO feature_toggle (tenant_id, feature_key, scope_type, is_enabled)
SELECT tenant_id, 'ATTACHMENTS_ENABLED', 'TENANT', TRUE FROM tenant WHERE tenant_code = 'SUBEX';
