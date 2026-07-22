# HS Notification Platform — Productization & HyperSense Integration Readiness
### Addendum to `CLAUDE_CODE_PROMPT.md` — Milestones 8–12

This extends the original 7 milestones (compile/smoke test → stub replacement →
auth → operational hardening → frontend polish → deployment). Those milestones
get the service *working*. These milestones get it *product-grade and
integration-ready* — able to sit inside HyperSense's cloud-native, microservices
architecture as a peer service rather than a bolt-on script, and reusable
across tenants beyond Zain without a fork.

Feed this whole file to Claude Code once milestones 1–4 are done. Each
milestone below is written the same way as the original ones: goal, tasks,
acceptance criteria.

---

## Milestone 8 — Formalize the invocation contract (Component C)

**Why:** The Alarm/Case/Analyst closure action integration layer is currently
unknown/unbuilt because it depends on HS's internal action framework. Rather
than wait, define our side of the contract now so integration becomes
"point HS's framework at this documented endpoint" instead of a joint
debugging session later.

**Tasks:**
- Define and implement `POST /api/v1/notifications/trigger` as the single,
  versioned entry point for all closure actions (Alarm, Case, Analyst, and
  future BMS/PAS).
- Payload contract: `tenantId`, `triggerType` (enum: `ALARM_CLOSE`,
  `CASE_CLOSE`, `ANALYST_CLOSE`), `entityId`, `context` (free-form map for
  trigger-specific data), `idempotencyKey`.
- Enforce idempotency: same `idempotencyKey` within a configurable window
  returns the original result, does not re-send.
- Version the contract explicitly (`/v1/`) so future changes don't silently
  break whatever HS eventually wires to it.
- Write a standalone contract doc (request/response examples, error codes)
  that could be handed to whoever owns HS's action execution framework
  without them needing to read the source.
- Write a conformance test suite (can be a Postman collection or integration
  tests) that any caller implementation could be validated against.

**Acceptance criteria:**
- Endpoint works when called twice with the same idempotency key — second
  call does not send a duplicate email.
- A person unfamiliar with the codebase could integrate against the contract
  doc alone.

---

## Milestone 9 — Make the SMTP config boundary a pluggable adapter

**Why:** The real HS SMTP config table DDL is still unknown. Hardcoding one
query blocks all downstream testing until that DDL arrives, and creates a
brittle single point of change.

**Tasks:**
- Introduce an `SmtpConfigProvider` interface (or equivalent) with a single
  method like `resolve(tenantId) -> SmtpConfig`.
- Implement a `LocalSmtpConfigProvider` (Mailpit/dev config) for testing
  today, and a `HsDbSmtpConfigProvider` placeholder for the real query, kept
  clearly separated so swapping in the real query is a one-file change.
- Confirm no code path anywhere reads SMTP credentials from the notification
  schema itself — this constraint must be provably true, not just assumed.
- Document exactly what's needed from HS to complete this (table name,
  column names, expected auth mechanism) so it's a clean ask when you get
  access to the real schema.

**Acceptance criteria:**
- Swapping providers requires no changes outside the provider implementation
  class.
- A grep across the codebase for SMTP credential fields returns zero hits
  outside the provider layer.

---

## Milestone 10 — Config-driven rules and tenant isolation hardening

**Why:** "Packaged product" means a fraud-ops admin can configure new
triggers, recipients, and tenants without a code deploy. Right now this is
partly seed data, partly implicit in code — that's a per-customer fork risk,
not a product.

**Tasks:**
- Confirm/build the rule editor screen (create/edit trigger→template→
  recipient→attachment mappings) if not already functional end-to-end.
- Enforce tenant scoping at the repository layer for every query (not just
  trusted to controller-level filtering) — add a repository-level test that
  intentionally tries to cross tenant boundaries and confirms it's blocked.
- Confirm feature toggles per tenant/alarm/action actually gate behavior at
  runtime, not just exist as stored flags.
- Add a documented, repeatable tenant onboarding procedure (migration +
  seed + config steps) — this should be a runbook, not tribal knowledge.

**Acceptance criteria:**
- Two seeded tenants (e.g., default + Zain) cannot see or trigger each
  other's data through any API path.
- A new trigger type can be added via admin UI/config without a code change.

---

## Milestone 11 — Reliability patterns for cloud-native operation

**Why:** HyperSense is built as a cloud-native microservices platform; this
service should behave like its siblings, not like a standalone script with a
health check bolted on.

**Tasks:**
- Add dead-letter handling for notifications that exhaust all retries —
  surfaced somewhere a human can see and manually requeue, not silently
  parked in a failed-status row.
- Add a circuit breaker around the SMTP call and any report-service
  attachment fetch, so a slow/down downstream doesn't exhaust threads.
- Make the health endpoint check real dependencies (DB connectivity, SMTP
  reachability) rather than returning unconditional `200 OK`.
- Emit structured logs (JSON, consistent fields: tenantId, triggerType,
  notificationId, status) suitable for shipping to a centralized log/metrics
  pipeline.
- Confirm watchdog restart-retry-escalate logic persists failure counts
  correctly across service restarts (don't let a restart silently reset the
  escalation counter).

**Acceptance criteria:**
- Killing the mail server (or Mailpit) causes the circuit breaker to open
  and the health endpoint to report degraded, not a hang or false-positive
  "healthy."
- A notification that fails all retries is visible and actionable, not just
  logged and forgotten.

---

## Milestone 12 — Packaging and versioning discipline

**Why:** For this to be a reusable product installed alongside HyperSense's
other microservices, it needs to be packaged the way that ecosystem expects.

**Tasks:**
- Convert raw K8s manifests into a Helm chart (values.yaml for per-tenant/
  per-environment config).
- Adopt semantic versioning on the API contract from Milestone 8 onward, so
  HS's integration layer isn't coupled to internal refactors.
- Document the tenant onboarding runbook (from Milestone 10) as an actual
  ops doc, not just code comments.
- Confirm the Docker image builds are reproducible and tagged properly
  (not `latest`-only) for rollback safety.

**Acceptance criteria:**
- `helm install` (or upgrade) works cleanly against a fresh namespace.
- A version bump to the API contract doesn't require touching deployment
  manifests unrelated to the change.

---

## Deferred to a later phase (explicitly out of scope for now)

These were identified as future-proofing for banking-grade scale. Don't let
Claude Code drift into building these before Milestones 8–12 are solid —
flag if asked to prioritize them early:

- Maker-checker approval workflow for template/rule changes
- Provable PII/DLP enforcement (audit-logged masking, not just present in code)
- Multi-channel notification architecture (SMS, Slack, etc. beyond email)
- Delivery confirmation (bounce/open tracking), not just "sent" status
- Full HA / multi-region deployment

---

## How to use this with Claude Code

1. Finish the original Milestones 1–4 first (compile, stub replacement, auth,
   operational hardening) — this doc assumes those are solid.
2. Feed Milestones 8 and 9 in parallel — they unblock real integration
   testing even before the real HS SMTP DDL is available.
3. Then Milestone 10 (config-driven rules, tenant isolation) — this is what
   makes the service reusable across customers instead of a Zain-only build.
4. Then Milestones 11–12 as you approach a real deployment target.
5. Do not start the "Deferred" list until 8–12 are done and verified against
   their acceptance criteria — use the audit prompt
   (`HS_NOTIFICATION_AUDIT_PROMPT.md`) to confirm before moving on.
