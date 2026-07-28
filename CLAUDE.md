# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Backend**: Spring Boot 3.3 / Java 17 / Spring Data JPA / Flyway / Quartz / Thymeleaf
- **Frontend**: React 18 / Vite / React Router v6 / Axios
- **Database**: PostgreSQL 16 (schema managed exclusively via Flyway migrations in `backend/src/main/resources/db/migration/`)
- **Mail**: real Subex SMTP relay (`subex-com.mail.protection.outlook.com:25`) — Mailpit has been removed; all outbound mail (including watchdog escalations) now goes through this relay, so avoid triggering test sends to real recipients

## Commands

### Run everything (recommended)
```bash
docker compose up --build
```

Starts Postgres, backend (`:8089`), and frontend (`:5173`). Mail is sent via the real Subex SMTP relay — no local mail catcher.

### Backend only
```bash
cd backend
mvn spring-boot:run
```

Flyway migrations run automatically on startup. Requires a local Postgres DB (`hs_notification`) and user (`hs_notify`).

### Frontend only
```bash
cd frontend
npm install
npm run dev
```

### Backend tests
```bash
cd backend
mvn test                     # all tests
mvn test -Dtest=ClassName    # single test class
mvn clean compile            # compile-check only
```

Tests use Testcontainers (spins up a real Postgres container) — Docker must be running.

### Frontend build
```bash
cd frontend
npm run build
```

## URLs (local)

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API (Swagger UI) | http://localhost:8089/docs |
| Actuator health | http://localhost:8089/actuator/health |
| Prometheus metrics | http://localhost:8089/actuator/prometheus |

## Auth

All API calls require the `X-HS-API-Key` header. The seeded dev key is `dev-local-key-change-me` for tenant `SUBEX`. This is configured in two places that must stay in sync:
- `backend/src/main/resources/application.yml` → `hs-notification.security.api-key-header`
- `frontend/src/api/client.js` → reads `VITE_API_KEY` env var, falls back to the hardcoded dev key

The API key is mapped to a tenant by `ApiKeyResolver` (currently a simple in-memory map from `application.yml`; Milestone 2 replaces this with a DB-backed table).

## Architecture

### Request flow (rule-based send)
```
HS workflow engine → POST /api/v1/notifications/send-by-rule
  → ApiKeyAuthFilter (resolves Tenant from key, sets request attr "resolvedTenant")
  → NotificationController
  → NotificationService.submitRuleBasedNotification()
      ├── idempotency check (notification_job.idempotency_key)
      ├── FeatureToggleService (NOTIFICATIONS_ENABLED toggle)
      ├── NotificationRuleRepository (rule must be ACTIVE + is_active=true)
      ├── RateLimitService (per-tenant, per-rule fixed-window counter)
      ├── TemplateRenderingService (Thymeleaf render + variable whitelist + PII masking)
      ├── AttachmentService (calls report service stub → PDF)
      └── MailDispatchService.attemptSend()
            ├── success → job.status = SENT, AuditService.log(SEND_SUCCESS)
            └── failure → handleFailure() → RETRYING (schedule next_retry_at) or FAILED/ESCALATED
```

### Retry/escalation state machine
Driven entirely by per-rule DB config (not code constants):
- `notification_rule.max_retry_count`, `retry_backoff_seconds`, `retry_backoff_multiplier`
- `RetryScheduler` polls for `RETRYING` jobs whose `next_retry_at` is past
- On final failure, `on_final_failure` field determines: `ESCALATE` → `EscalationService` sends to `escalation_chain` steps; `HOLD_FOR_MANUAL` → surfaces in job queue; `DROP` → logged and discarded

### Watchdog
`WatchdogScheduler` polls Spring Actuator health every N seconds (configured in `watchdog_config` table), writes every check to `watchdog_health_log`, and emails escalation recipients when consecutive failures exceed the threshold.

### Multi-tenancy
Every DB table has a `tenant_id` FK. The tenant is resolved from the API key in `ApiKeyAuthFilter` and stored as a request attribute — controllers must never trust a client-supplied tenant ID.

### Audit log
`notification_audit_log` is append-only. Every write path (job creation, send attempt, retry, escalation, rule/template changes) must write to it via `AuditService`. Do not add delete or update operations to this table.

## Key constraints (do not violate)

- **DLP**: `TemplateRenderingService` enforces a variable whitelist (`allowed_variables` column) and PII masking (`pii_mask_fields` column). Never remove or bypass these checks.
- **Idempotency**: `notification_job.idempotency_key` prevents duplicate sends from retried workflow calls. Always check before creating a new job.
- **Schema changes**: add a new Flyway migration file (`V3__...sql`, etc.) — never modify existing migration files. Flag data model changes in a comment and confirm before migrating.
- **Per-rule retry config**: keep retry/backoff/escalation config in the DB (`notification_rule` columns), not in code constants.
- **Tenant isolation**: all repository queries must scope by `tenant_id`.

## Stubbed integration points (TODOs)

- `AttachmentService.callReportService(...)` — wired; configure `hs-notification.report-service.base-url` to enable
- `ApiKeyResolver` — DB-backed via `api_key` table (V3 migration); `AdminController` manages key lifecycle
- Maker-checker — enforced in `RuleController.approve()` (approver ≠ creator)
- Multi-channel — EMAIL / WEBHOOK / SLACK are live; SMS is a stub pending gateway config (`SmsChannelSender`)

## Channel dispatch architecture

`MailDispatchService` is now a channel router. It auto-discovers all `ChannelSender` beans and routes each job
by `rule.template.channel`, defaulting to EMAIL for direct sends.

| Channel | Implementation | Config needed |
|---|---|---|
| EMAIL | `EmailChannelSender` | Spring mail (already wired) |
| WEBHOOK | `WebhookChannelSender` | Put target URL(s) in `to_addresses` |
| SLACK | `SlackChannelSender` | Put Slack incoming webhook URL(s) in `to_addresses` |
| SMS | `SmsChannelSender` (stub) | Wire `hs-notification.sms.gateway-url` (Milestone 4) |

## Notification rule lifecycle

`DRAFT` → `PENDING_REVIEW` → `APPROVED` → `ACTIVE` → `DISABLED`

Only rules with `status = 'ACTIVE'` AND `is_active = true` will fire. Seeded rules: `PR_CLOSE_RULE` (ACTIVE), `CASE_ESCALATE_RULE` (PENDING_REVIEW).
