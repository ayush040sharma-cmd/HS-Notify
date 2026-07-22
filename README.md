# HS Notify

A multi-tenant, DB-driven notification platform for HS fraud-ops workflows
(Alarm/Case/Analyst closure actions) — built on Spring Boot + React, replacing
one-off custom scripts with an auditable, governed email/WhatsApp notification
engine that HS's workflow engine (and eventually Subex HyperSense) can call
over a simple REST API.

## Background — why this exists

Zain's PR-closure email requirement (a one-off customer script: "when a PR
closes, email fraud-ops") is what triggered this project, but the actual goal
is broader: HyperSense has no clean, reusable outbound-notification API today.
Every customer that needs "notify someone when X happens" would otherwise get
a bespoke script. This platform is meant to close that gap once, as a
standalone, packaged service HS can call for *any* tenant — not a Zain
customization — so config changes per customer live in DB rows (rules,
templates, recipients), never in code.

The design follows five capability layers:

1. **Trigger** — HS/BMS calls this service over REST when something happens
   (PR closed, case created, case escalated, ...).
2. **Notification rule** — subject, To/CC, attachment, retry/escalation policy
   are all per-DB-row config, not code, so onboarding a new trigger or tenant
   doesn't require a redeploy.
3. **Execution** — render the template (DLP whitelist + PII masking), attach a
   file if configured, send via the routed channel.
4. **Ops** — append-only audit trail, health watchdog with auto-escalation,
   per-tenant rate limiting.
5. **Packaging** — deployable alongside HS, logically separate, supportable by
   PS/Ops without reading source.

See `CLAUDE_CODE_PROMPT.md` and `HS_NOTIFICATION_MILESTONES_8-12.md` for the
detailed milestone history and what's planned next.

## What's built

- **Multi-tenancy** — every table is scoped by `tenant_id`, resolved
  server-side from the caller's API key (`ApiKeyAuthFilter`); controllers
  never trust a client-supplied tenant.
- **Two-tier auth** — workflow/API callers authenticate with an
  `X-HS-API-Key` header (hashed, DB-backed, rotatable — managed from
  **Users & Keys**); the operator dashboard additionally sits behind an
  admin login screen (JWT session via `AdminAuthController`).
- **Notification rules** with a real maker-checker lifecycle —
  `DRAFT → PENDING_REVIEW → APPROVED/ACTIVE → DISABLED`, and approving a rule
  or template rejects if the approver is the same person who created it
  (enforced in code, not just schema).
- **Templates** with a DLP variable whitelist and PII field masking
  (`TemplateRenderingService`), versioned, with their own maker-checker
  approve/activate step and a full audit trail on every create/update.
- **Recipients** — reusable named recipient groups, or type To/CC addresses
  directly into a rule and a dedicated group is created/maintained for you.
- **Attachments per rule** — pick **None**, **PR Record** (auto-fetched as a
  PDF from the report service at send time), or **Image / PDF** (upload a
  file once, attached to every send), with a configurable failure policy
  (send anyway / hold for review / fail the job).
- **Retry, backoff and escalation** — fully per-rule DB config (not code
  constants); `RetryScheduler` retries failed sends with exponential
  backoff, and on final failure either escalates through a configured chain,
  holds the job for manual review, or drops it, per the rule's setting.
- **Channels** — EMAIL is live (SMTP via the real Subex mail relay). WHATSAPP
  has a real, persisted config screen (**Channels → WhatsApp**: business
  account ID, phone number ID, webhook URL, API key) so an admin can fill
  everything in ahead of time — only the actual send call
  (`WhatsAppChannelSender`) is still stubbed, pending the real WhatsApp
  Business API integration.
- **Computed template variables** — most variables come straight from the
  trigger's context payload, but some are derived at send time instead of
  being stored anywhere. Today that's `case_link`
  (`NotificationService.withComputedVariables`), built as
  `{hs-notification.case-link.base-url}/{case_id}` whenever the context
  includes a `case_id`. Safe to reference `{{case_link}}` in any template's
  `allowedVariables` without the caller ever having to supply it.
- **HyperSense PAS integration lookups** — `GET /api/v1/templates/active` and
  `GET /api/v1/rules/active` return a small, flat list of active
  templates/rules for populating a dropdown in PAS's analyst action screen.
  Deliberately outside the normal tenant-API-key/admin-JWT auth (PAS can't
  carry either) — gated instead by a single shared header,
  `X-HS-Lookup-Token` (see `LookupTokenFilter`, config
  `hs-notification.security.lookup-token`). Both accept an optional
  `tenantCode` query param; omit it during the PAS integration's transition
  period and you get all tenants back, with a `WARN` log line each time so
  there's visibility into who still needs to start passing it.
- **Watchdog** — polls Spring Boot Actuator health on an interval, logs every
  check, and emails the escalation chain once consecutive failures cross a
  threshold.
- **Operator UI** — Dashboard (24h + all-time KPIs, delivery health, live
  system status, recent jobs), Job Queue, Failed & Escalated, Notification
  History, Metrics, System Logs, and an append-only Audit Log — all wired to
  real APIs, with light/dark theme support.

## Stack

- **Backend**: Spring Boot 3.3 / Java 17 / Spring Data JPA / Flyway / Quartz / Thymeleaf
- **Frontend**: React 18 / Vite / React Router v6 / Axios
- **Database**: PostgreSQL 16 (schema managed exclusively via Flyway migrations)
- **Mail**: real Subex SMTP relay (`subex-com.mail.protection.outlook.com:25`) — no local mail catcher

See `CLAUDE.md` for the full request-flow architecture, retry/escalation
state machine, and constraints that must not be violated (DLP checks,
idempotency, tenant scoping, append-only audit log).

## What's intentionally stubbed

- **WhatsApp sending** (`WhatsAppChannelSender`) — the config screen and DB
  persistence are real (see above), but the send call itself just logs the
  intent and marks the job delivered, so the rest of the pipeline (audit,
  retry state machine) works end-to-end; wire the real WhatsApp Business API
  when that phase starts, reading from the `whatsapp_config` table that's
  already populated.
- **Report service** (`AttachmentService.callReportService`) — makes a real
  HTTP call and expects real PDF bytes back; set
  `hs-notification.report-service.base-url` to your report service (or a
  local mock) before relying on "PR Record" attachments.
- **CSV/report attachment for non-mock cases** — the real production
  attachment source is `COPY ... TO STDOUT` against the case tables, not an
  HTTP report service call; not yet built.

## Quick start (Docker Compose — recommended)

```bash
docker compose up --build
```

Starts Postgres, the backend (port 8089), and the frontend (port 5173).
Outbound mail goes through the real Subex SMTP relay
(`subex-com.mail.protection.outlook.com:25`) — there is no local mail catcher,
so test sends reach real inboxes.

## Quick start (manual / no Docker)

**1. Postgres**

```bash
createdb hs_notification
createuser hs_notify --pwprompt   # set password to match application.yml / your .env
```

**2. Backend**

```bash
cd backend
mvn spring-boot:run
```

Flyway runs the migrations automatically on startup:
`V1__init_schema.sql` (core schema), `V2__seed_data.sql` (seed tenant/rules/
templates), `V3__api_key_table.sql` (DB-backed hashed API keys),
`V4__update_watchdog_escalation_recipient.sql` (real escalation recipient),
`V5__whatsapp_config_table.sql` (WhatsApp config persistence).

**3. Frontend**

```bash
cd frontend
npm install
npm run dev
```

## URLs (local)

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API (Swagger UI) | http://localhost:8089/docs |
| Actuator health | http://localhost:8089/actuator/health |
| Prometheus metrics | http://localhost:8089/actuator/prometheus |

## Default dev credentials

- **API key** (for direct API calls, e.g. `POST /api/v1/notifications/send-by-rule`):
  `X-HS-API-Key: dev-local-key-change-me`, mapped to tenant `ZAIN`. On first
  boot this is auto-seeded into the DB-backed `api_key` table (hashed) — from
  then on, keys are managed from **Users & Keys** in the UI, not config.
- **Admin login** (for the operator dashboard): `admin` / `admin123` — dev
  only, set via `HS_ADMIN_USERNAME` / `HS_ADMIN_PASSWORD`.
- **PAS lookup token** (for `GET /api/v1/templates/active` /
  `GET /api/v1/rules/active`): header `X-HS-Lookup-Token`, set via
  `HS_LOOKUP_TOKEN`. Dev default is a placeholder in `application.yml` — this
  repo's actual running value is a generated secret set only via env var, not
  committed anywhere.

Change all three before deploying anywhere real.

## Testing the end-to-end flow

1. Open the frontend and log in with the admin credentials above.
2. Go to **Notification Rules** — `PR_CLOSE_RULE` and `CASE_ESCALATE_RULE` are
   both `ACTIVE`; `NEW_CASE_ALERT_RULE` is seeded `DRAFT` (it has a
   placeholder, non-deliverable recipient — replace before submitting it for
   review). Approving a rule or template requires a *different* user than its
   creator (maker-checker).
3. Go to **Send Notification** → send via `PR_CLOSE_RULE` (uses `case_id`,
   `case_template_name`, `detection_time`, `record_count` — the real
   production field names, not the original placeholder ones) — it'll also
   fetch its configured PR Record attachment. Or send directly against the
   `NEW_CASE_ALERT_MAIL` template to see the computed `case_link` in action.
4. Check **Job Queue** / **Notification History** — the job should show
   `SENT` (or `RETRYING`/`FAILED` if the Subex mail relay isn't reachable).
5. Check **Audit Log** — `JOB_CREATED` and `SEND_SUCCESS` (or
   `SEND_FAILED`/`RETRY_SCHEDULED`) entries.
6. Check **Watchdog** — the health log should be populating every few
   seconds with real `UP` entries.
