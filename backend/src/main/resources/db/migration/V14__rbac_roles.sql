-- Phase 5: RBAC. app_user gains a real password so dashboard login can be
-- role-aware instead of the single hardcoded admin-login.username/password
-- pair everyone shared. The existing 'admin' row (seeded in V2, role=ADMIN)
-- is grandfathered — UserAuthSeeder hashes the configured
-- hs-notification.security.admin-login.password into it at startup the
-- first time password_hash is null, so admin/admin123 keeps working
-- unchanged. Every other account gets its password set at creation time via
-- POST /api/v1/users (ADMIN only) — never seeded here.
ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(255);

COMMENT ON COLUMN app_user.role IS 'ADMIN | RAFM_HEAD | MANAGER | ANALYST | VIEWER';
