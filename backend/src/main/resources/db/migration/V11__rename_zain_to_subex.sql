-- Reverses V10's approach: the operator decided to rename the existing ZAIN
-- tenant in place (keeping its rules/templates/api_keys/jobs/audit history)
-- rather than run a separate fresh SUBEX tenant alongside it. Delete the
-- fresh V10 SUBEX tenant and its dependents first (frees up the tenant_code
-- unique constraint), then rename ZAIN -> SUBEX in place. Any API key issued
-- against the V10 SUBEX tenant_id is deleted here — the ZAIN tenant's
-- existing keys (which now authenticate as SUBEX) remain valid unchanged.
DELETE FROM api_key WHERE tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'SUBEX');
DELETE FROM feature_toggle WHERE tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'SUBEX');
DELETE FROM tenant WHERE tenant_code = 'SUBEX';

UPDATE tenant SET tenant_code = 'SUBEX', tenant_name = 'Subex' WHERE tenant_code = 'ZAIN';
