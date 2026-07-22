# Claude Code prompt: harden HS Notification Platform for production

Copy everything below the line into Claude Code (in this project's root,
opened in VS Code) as your starting instruction. It's written so Claude
Code can work through it section by section across multiple sessions —
treat each numbered section as a milestone you can commit independently.

---

## Context

This repository is a working skeleton of the HS Notification Platform: a
packaged microservice that lets HS (and customers like Zain) trigger
controlled outbound emails from Alarm/Case/Analyst closure actions, with
DB-driven config, per-rule retry/escalation policy, a watchdog, and an
audit trail. Stack: Spring Boot 3 / Java 17 / Postgres / Flyway / React
(Vite). Read `README.md` first for what's already working and what's
intentionally stubbed.

Your job is to take this from "working skeleton" to "production-ready
platform" across the milestones below. Work through them in order — later
milestones assume earlier ones are done. After each milestone, run the
existing tests (and any you add), make sure the app still boots via
`docker compose up --build`, and commit before moving to the next one.

Ask me before making product decisions that aren't purely technical
(e.g. "should disabled rules still appear in the audit log filter?") —
but don't ask permission for implementation details within a milestone.

---

## Milestone 1 — Make it compile and pass a real smoke test

- Run `mvn clean compile` in `backend/` and fix any compilation errors —
  I built this quickly across many files by hand and there may be import
  mismatches or missed beans I didn't catch.
- Run `mvn test`. There are currently no tests — write at least:
  - A `@SpringBootTest` using Testcontainers Postgres that boots the full
    context, runs Flyway migrations, and asserts the seed data loaded
    (the ZAIN tenant, PR_CLOSE_RULE active, CASE_ESCALATE_RULE pending).
  - A unit test for `TemplateRenderingService` covering: variable
    whitelist enforcement (unlisted variables render empty, not echoed),
    PII masking, and HTML escaping of interpolated values.
  - A unit test for `MailDispatchService.computeBackoff` covering the
    exponential backoff math across several attempt counts.
- Get `docker compose up --build` to a state where the frontend loads,
  the dashboard shows real (non-zero, non-stuck) data from the seeded
  job, and sending a test notification via the UI actually produces an
  email visible in Mailpit at localhost:8025.

## Milestone 2 — Replace the stubbed integration points

- `AttachmentService.callReportService(...)`: replace the stub with a real
  HTTP client call (RestClient or WebClient) to a configurable report
  service base URL (add `hs-notification.report-service.base-url` to
  `application.yml`). Handle timeout, 4xx, and 5xx responses distinctly so
  `on_generation_failure` policy (SEND_WITHOUT_ATTACHMENT / HOLD_JOB /
  FAIL_JOB) can be exercised in tests for each failure mode.
- `ApiKeyResolver`: replace the env-var map with a real `api_key` table
  (new Flyway migration) storing a salted hash of each key, a tenant_id
  FK, an optional expiry, and a revoked flag. Add an admin endpoint to
  issue/revoke keys (protect this endpoint itself with a separate
  bootstrap admin credential, not a tenant API key).
- Add the maker-checker check in `RuleController.approve`: reject with 409
  if `approvedBy` would equal `createdBy`, once you've wired in a real
  identity (see Milestone 4 for auth) — for now, accept a `username` claim
  via a header and use that.

## Milestone 3 — Multi-channel and delivery confirmation

- Generalize `MailDispatchService` behind a `NotificationChannel` interface
  with an `EmailChannel` implementation (current behavior) and stub
  `SmsChannel` / `WebhookChannel` implementations that throw
  `UnsupportedOperationException` with a clear message — the goal here is
  the abstraction boundary, not full SMS/webhook support yet.
  `notification_template.channel` already exists in the schema to support
  this; `NotificationRule` resolution should dispatch to the channel
  matching its template's channel field.
- Add bounce/delivery handling: either (a) integrate with your SMTP
  provider's webhook for bounce notifications and update
  `notification_job.delivery_confirmed` / `bounce_reason`, or if no
  provider webhook is available yet, add a manual
  `POST /api/v1/jobs/{jobId}/mark-bounced` endpoint operators can use, and
  make a bounce trigger the same escalation path as a final send failure.

## Milestone 4 — Real authentication and authorization

- Replace the simple API-key-to-tenant mapping with a layered model:
  - **Inbound HS workflow calls** (Alarm/Case/Analyst closure actions):
    keep API-key auth but source keys from the Milestone 2 `api_key`
    table, and add request signing (HMAC over body + timestamp) to guard
    against replay, since these calls carry no human review step.
  - **Operator UI calls** (the React app): move to a proper session —
    either OAuth2/OIDC against HS's existing identity provider if one
    exists, or a JWT issued by a `/api/v1/auth/login` endpoint backed by
    the `app_user` table (add password hash storage with bcrypt, or better,
    confirm with me whether HS already has an SSO provider to integrate
    with instead of inventing local auth).
  - Enforce `app_user.role` (VIEWER/OPERATOR/APPROVER/ADMIN) at the
    controller level: VIEWER can only GET, OPERATOR can requeue/cancel/send
    direct, APPROVER can approve rules, ADMIN can do everything including
    manage SMTP config and API keys.
- Add tenant isolation tests: write an integration test that creates two
  tenants, confirms tenant A's API key cannot read/modify tenant B's rules,
  jobs, or audit log under any endpoint.

## Milestone 5 — Operational hardening

- **Watchdog**: the current implementation checks its own Actuator health
  endpoint, which only proves the JVM is up, not that it can actually reach
  Postgres or the SMTP relay end-to-end. Extend the health check to do a
  lightweight synthetic check (e.g. a `SELECT 1` plus an SMTP connection
  test without sending mail) so "UP" actually means "can do its job."
  Wire Spring Boot's liveness/readiness probes for k8s-style deployment so
  an external supervisor can restart the process — document this clearly
  since the current in-process watchdog cannot restart its own JVM.
- **Rate limiting**: the current `RateLimitService` is a basic fixed-window
  counter. Add a per-tenant override (some tables already support this via
  `smtp_config.max_per_minute`; wire it through rather than the hardcoded
  `DEFAULT_MAX_PER_MINUTE`).
- **Retention**: add a scheduled job that purges `notification_audit_log`
  and `notification_job` rows older than the configured retention (see
  `hs-notification.retention.*` in `application.yml`) — but check with me
  on retention requirements first, since fraud-ops compliance may require
  longer retention than a typical operational log.
- **Observability**: add structured logging (JSON) for production, wire
  Micrometer counters for jobs sent/failed/escalated per tenant (the
  Prometheus registry dependency is already in the POM), and add a Grafana
  dashboard JSON (or instructions) for the metrics that matter: send
  success rate, retry rate, escalation rate, watchdog uptime.

## Milestone 6 — Frontend polish and missing screens

- Add a tenant switcher to the UI header for HS support staff who manage
  multiple customers (currently hardcoded to a single API key/tenant).
- Add a rule editor (create/edit form, not just list+approve) for
  NotificationRule, NotificationTemplate, RecipientGroup, and
  AttachmentRule — the backend already has the CRUD-shaped repositories;
  add the missing create/update endpoints and forms.
- Add pagination controls to Job Queue and Audit Log (backend already
  returns Spring Page objects; the frontend currently only requests page 0).
- Add a template preview: given a template + sample context JSON, call a
  new POST /api/v1/templates/{id}/preview endpoint that returns rendered
  subject/body without creating a job, and show it live in the rule editor.

## Milestone 7 — Deployment

- Write a Helm chart or Kubernetes manifests (confirm with me which) for
  the backend, with readiness/liveness probes wired to Milestone 5's real
  health check, and a Postgres connection via a Secret rather than env vars
  in plaintext.
- Document the HS-side "action adapter" integration point referenced in
  the architecture (item C) — this repo can't build HS's own backend
  adapter, but document the exact request/response contract
  (POST /api/v1/notifications/send-by-rule, headers, idempotency key
  requirements, error codes) clearly enough that whoever builds the HS-side
  adapter has everything they need.

---

## Ground rules while you work

- Don't weaken the multi-tenancy or audit-log immutability while
  "simplifying" anything — every write path must keep writing to
  notification_audit_log.
- Don't remove the variable whitelist / PII masking in
  TemplateRenderingService even if it seems like dead weight in tests —
  it's a deliberate DLP control for a fraud-ops product handling account
  and case data.
- Keep the per-rule retry/backoff/escalation config in the DB, not in code
  constants — that configurability is a named functional requirement.
- Flag anything you think should change about the data model in a comment
  and ask me, rather than silently migrating it — schema changes touch
  the audit trail's integrity.
