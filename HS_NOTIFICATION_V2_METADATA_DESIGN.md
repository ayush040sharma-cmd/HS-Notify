# HS Notify v2 — Metadata-Driven Notification Platform for HyperSense

This is the design record for evolving HS Notify from an email-send API into
a metadata-driven notification platform that drives HyperSense's Analyst
Actions panel. It captures the vision-vs-reality summary, the verified
schema facts, the honest capability matrix, the registry deltas (some
already shipped, some still designed-only), and the leverage list for
HyperSense's own platform team. Treat this as a companion to
`HS_NOTIFICATION_MILESTONES_8-12.md` — that doc sequences *when* work
happens; this one defines *what* the metadata layer actually needs to be.

---

## Vision vs Reality (plain language)

**The vision**: an analyst clicks one button in HyperSense's case screen.
Behind that single click, HS Notify should figure out — with zero
per-customer code and no HyperSense-side intelligence — who to notify, what
template and attachments to use, and whether the analyst is even allowed to
do this. One API call in, everything resolved automatically by metadata
sitting in our own database.

That vision splits cleanly into two buckets: work we can do entirely
ourselves, and work that needs HyperSense's platform team to change
something first. Here's exactly where that line sits today.

### (a) Already built, or buildable now, entirely on our side

- **Picking the right template and filling it in safely** — done and
  enforced (variable whitelist, PII masking).
- **Attaching the right files** — mostly done. Case PDF and PR-records
  exports work today; a case-evidence attachment (real files attached to a
  case) is designed and ready to build, using a data chain we've now
  verified end-to-end.
- **Figuring out who to email** — mostly done. For people outside Subex
  (vendors, partners), it's just a plain text box, no lookup needed. For
  Subex/internal staff, we found a real, current username→email table we can
  query directly (see below) — no need to build a connection into
  HyperSense's separate login system for this.
- **Knowing which "actions" exist and what fields they need** — done. We
  already expose a list HyperSense could read from automatically.
- **Making our own richer form-builder speak HyperSense's simpler language**
  — done. We can now mark a field as one of HyperSense's 5 basic types
  (text, yes/no, number, list, nested list) even though our own admin tools
  support fancier things like dropdowns.

### (b) Blocked — needs HyperSense's platform team to change something

- **Knowing *who* clicked the button.** This is the big one. Today,
  HyperSense doesn't tell us which analyst submitted an action — only which
  customer/tenant. Without that, we can't enforce "only a Manager can send
  this," we can't log who really did it, and we can't auto-fill "email me
  the case owner" reliably.
- **Real dropdowns, checkboxes-that-show-more-fields, or picker widgets.**
  HyperSense's action panel only draws plain text boxes and yes/no
  checkboxes — confirmed by testing it and by reading its own code. No
  amount of metadata on our side can make it draw a dropdown.
  - Note: for internal-user recipients specifically, this doesn't block
    resolution — that's handled server-side by our own username→email
    lookup, so the analyst never needs to pick anyone from a dropdown at
    all. The blocker only matters for cases where an analyst genuinely needs
    to *choose* between options themselves.
- **Telling the analyst whether it worked.** Right now, after clicking
  submit, HyperSense shows a generic "done" message no matter what actually
  happened on our end (sent, rejected, rate-limited). Fixing that needs
  HyperSense to actually display what we send back.
- **HyperSense automatically syncing its button list from us.** We already
  publish the list of available actions; HyperSense's team would need to
  actually read from it instead of hand-configuring each button.

The throughline for a non-engineer: **everything that's just "figure out the
right data and fill it in" is ours to build. Everything that's "know who's
asking, or let them choose from options" needs HyperSense's platform team.**

---

## The one non-negotiable constraint

HyperSense's Analyst Actions panel renders a flat, ordered list of key/value
fields (`STRING` / `BOOLEAN` / `INTEGER` / `ARRAY` / `OBJECT`-with-nested-list)
as plain text boxes and checkboxes, and binds STATIC/DYNAMIC values into a
request body. That is the entire engine. No option-dropdowns, no conditional
visibility, no object-type awareness, no dynamic buttons from an API
response, no picker widgets (one hardcoded exception: "Add Hotlist").
Confirmed empirically and by reading the compiled PAS frontend bundle. Every
design decision below works *around* this constraint, not against it — the
brain (this backend) pre-resolves everything down to a flat field list;
HyperSense never sees an object type, a conditional, or an option list.

## Architecture model

1. **HyperSense** — dumb flat-field renderer. Zero intelligence.
2. **Metadata "brain"** — HS Notify's own backend. All intelligence lives
   here; it reads the metadata registries and pre-resolves everything down to
   the flat field list HyperSense can draw.
3. **Business systems** (Case Mgmt, PR, Fraud, BMS) expose data only, never
   notification logic.

---

## Verified schema facts (172.24.111.102:32704, live queries, 2026-07-31)

The cluster hosts 31 databases; `information_schema` only sees the connected
one, so every claim below was checked by iterating `pg_database` and querying
each database individually — a single-database check is how the *previous*
`case_tbl` investigation went wrong (see `project_case_tbl_blocked` memory),
and how a second, independent manual check on this task initially concluded
`case_artifact` didn't exist (it was checking `postgres`, not
`casemanagement`).

### Attachment lookup chain — confirmed real, EVIDENCE provider designed to un-stub

`case_artifact` and its sibling tables (`case_doc_artifact`,
`case_chart_artifact`, `case_table_artifact`, plus `_archive` variants) exist
**only** in the `casemanagement` database. Swept all 31 databases for
`%artifact%|%attach%|%document%|%file%` — nothing else is a case-attachment
table (hits elsewhere are unrelated DMS file-telemetry/profiling tables in
`dmw`/`postgres`, and Activiti's own `workflowengine.act_hi_attachment`).

`case_artifact` is **not** the file table itself — it holds
findings/properties metadata and is keyed by `case_workstep_id`, not
`case_id` directly. The real chain to fetch attachments by case id:

```
case_id
  → case_workstep.case_id = case_id                          (case_workstep.id)
  → case_artifact.case_workstep_id = case_workstep.id         (case_artifact.id, unique per workstep)
  → case_doc_artifact.case_artifact_id = case_artifact.id     (document bytea, name, sub_type)
```

Verified end-to-end with a live join (e.g. `case_id 368755` → 4 real files:
pdf/xlsx/png/txt, 293–319,530 bytes). Current volume is thin — 47 rows in
`case_artifact`, 12 in `case_doc_artifact` DB-wide — **flag as low-coverage
in the provider's description, not proven-at-scale.** This is the chain the
`EVIDENCE` attachment provider (currently a stub, `isAvailable() = false`)
should be un-stubbed against.

### Internal user/email directory — confirmed real via a same-cluster SQL mirror

No email column exists anywhere inside `casemanagement` (`user_option`,
`user_preference_tbl`, `workstep_users`, etc. all key by bare `user_id`/
`user_name`, HyperSense-style usernames like `ayush.sharma`) — this part of
the original "no directory in Postgres" read is correct. A broader
31-database sweep, however, found `appmonitoringmetricservice.users`
(`user_name`, `email_id`, `status`, `last_updated_time`) — 90 rows, refreshed
the same day as this investigation — plus a companion view
`sysadmin_login_user_detail_v` over the same data. This table is easy to miss
precisely because its name gives no hint it holds identity data.

Cross-checked against real `case_tbl.current_assigned_user` values: **10 of
14** distinct case-owner usernames match directly, each with a real,
non-guessable email:

| username | real email (from `appmonitoringmetricservice.users`) |
|---|---|
| `pawan` | `pawan.tiwari@subex.com` |
| `rohith.r` | `rohith.raju@subex.com` |
| `fraud_analyst` | `fraud.user@subex.com` |
| `momo_fraud_user` | `momofraud@subex.com` |
| `subramoni.subramoni` | `subramoni.darmarajan@subex.com` |

**This proves a live bug in the current codebase.** `NotificationService`'s
existing `CURRENT_USER` fallback (`username + "@" + owner-email-domain`,
default `subex.com`) would synthesize `pawan@subex.com` and
`rohith.r@subex.com` for the two rows above — both wrong.

**Design decision (confirmed):** treat `appmonitoringmetricservice.users` as
the primary resolver for internal/Subex recipients — it's SQL-reachable,
verified accurate against real data, and needs no HTTP integration. Working
theory, not yet confirmed with the table's owner: it's a login-sync mirror
*of* Keycloak (the `sysadmin_login_user_detail_v` naming and
`active`/`blocked` status semantics both point that way), which is why a
Keycloak-only investigation wouldn't surface it — Keycloak stays the
authoritative identity system, this is just a same-cluster readable copy.
Keep `username + "@" + domain` synthesis only as the last-resort fallback for
usernames absent from the directory. External/non-Subex recipients (vendors,
partners) stay exactly what they already are: a plain free-text EMAIL field
— a flat STRING to HyperSense, no lookup involved, no change needed there.

*Aside, unrelated to this design*: the same cluster also has an `hs_notify`
**schema** with tables like `hs_notification_recipients`,
`hs_notification_rules` — names don't match any table in this repo's Flyway
migrations. This app's real runtime database is a separate Postgres entirely
(local Docker for dev, port 5434 on the deployed box) per `CLAUDE.md`, not
this shared analytics cluster. Naming coincidence with some other system —
noted so it isn't confused with our schema later, not investigated further.

---

## What's already built (inventory, current as of this doc)

- **Notification Action Registry** (`notification_action`, V7) — replaces
  the old hardcoded scenario constants. `roleRequired` column exists,
  editable via admin API, but is **still not read on the send path** — no
  per-caller identity reaches `/api/v1/notify` today beyond a tenant-scoped
  API key, so there's nothing to check it against yet (see Platform Ask #1).
- **Form Metadata Engine** (`form_schema`/`form_fields`/`field_options`/
  `field_validation`, V8) — its `field_type` enum models UI *widgets*, built
  assuming a smart frontend (the dashboard's own ad-hoc-send wizard), not
  HyperSense's flat renderer. **Now bridged** — see V15 below.
- **Attachment Schema/Provider system** (`attachment_schema`/
  `attachment_schema_provider`, V13) — fully wired, provider registry
  auto-discovers `AttachmentProvider` beans by key. Of 7 providers, only
  `CASE_PDF`, `PR_RECORDS`, `EXCEL_EXPORT` are real; `CDR_SUMMARY`,
  `SUBSCRIBER_HISTORY`, `DASHBOARD_SNAPSHOT`, `EVIDENCE` are honest stubs
  (`isAvailable() = false`). `EVIDENCE` is designed to un-stub (above),
  **not yet implemented**.
- **Recipient resolution** — static `recipient_group`/`recipient_group_member`
  plus dynamic `CURRENT_USER` mode (V12) fully built and wired into
  `NotificationService.resolveCurrentUserRecipient()`. Currently still uses
  the domain-guess fallback described above as its only mechanism for
  usernames — the `appmonitoringmetricservice.users` lookup is designed,
  **not yet implemented**. `hsUserRef` column on `RecipientGroupMember`
  exists but is never read anywhere (dead column, reserved tie-in point).
- **Action discovery is already live** as unauthenticated lookup-token
  endpoints — `GET /api/v1/actions`, `GET /api/v1/actions/{code}/schema`,
  `X-HS-Lookup-Token` auth, no tenant context.
- **DLP** (`TemplateRenderingService`) — variable whitelist + PII masking,
  enforced, but **scoped per-template only**. The raw `payload`/
  `effectiveContext` map reaches attachment providers completely unfiltered
  — template rendering and attachment generation are not the same trust
  boundary today. **Not yet fixed.**
- **RBAC** (V14) — real per-user roles enforced on the admin/dashboard
  surface via JWT. `/api/v1/notify` and `/api/v1/notifications/**` remain
  JWT-exempt, API-key-only — why `roleRequired` can't be enforced yet.
- **V15 migration — shipped**: `form_fields.hs_field_type`,
  `notification_action.hypersense_exposed`,
  `notification_action.context_resolver_key` all added, backfilled, and
  wired through the Java model/DTO/controller layer and the React admin
  pages (`NotificationActions.jsx`, `FormSchemas.jsx`). Save-time validation
  is in place: a `hypersense_exposed` action cannot be linked to a
  `form_schema` containing a conditional field. This has been compile- and
  build-verified but **not yet run against a live database** (no local
  Postgres/Docker available in this environment at the time of writing).
- Current migration head: **V15**. Next migration for further work: **V16**.

---

## Missing Metadata Matrix

One row per capability. Tagged honestly: **BUILDABLE-NOW** (backend-resolved,
needs nothing from HyperSense's platform team), **EXTERNAL-DEPENDENCY**
(needs a call to a system we don't own, but not blocked on HyperSense
changing anything), or **PLATFORM-BLOCKED** (needs a HyperSense change we
don't control — blocker named).

| Capability | Current state | Tag | Detail |
|---|---|---|---|
| **Template** | `NotificationTemplate` + DLP whitelist/masking, fully built and enforced. Actions pre-bind a template server-side. | **BUILDABLE-NOW** | No HyperSense-visible field needed — the pattern that already works with a dumb renderer. |
| **Attachment** | `attachment_schema`/`attachment_schema_provider` fully built; brain resolves the provider set server-side via `notification_action.attachment_schema_id`. | **BUILDABLE-NOW** | `EVIDENCE` stub design-ready using the verified `case_id → case_workstep → case_artifact → case_doc_artifact` chain — thin volume (12 rows DB-wide), flag accordingly in the provider description. Not yet implemented. |
| **Recipient** | Static group + dynamic `CURRENT_USER` mode built, resolved server-side. Internal-user emails: `appmonitoringmetricservice.users`, a same-cluster SQL mirror (verified accurate, likely Keycloak-synced). External emails: plain free-text field, no lookup. | **BUILDABLE-NOW** for internal (via the mirror table) / **BUILDABLE-NOW** for external (already a flat field) | No HTTP Keycloak Admin API integration needed for the common case. Fixes a live wrong-email bug in the existing domain-guess fallback. If the mirror table's sync guarantees ever prove unreliable, the design falls back to `EXTERNAL-DEPENDENCY (Keycloak Admin API)` — noted as the contingency, not the plan. |
| **Action** | `notification_action` registry + lookup-token discovery already live. | **BUILDABLE-NOW** (our side) / **PLATFORM-BLOCKED** (PAS auto-sync) | HS Notify's side is done; the PAS team statically wiring each button to read from our endpoint is a smaller integration ask than originally scoped. |
| **Variable** | DLP whitelist enforced per-template; PII masking now also applied to the attachment-generation context. | **BUILDABLE-NOW** — shipped | `TemplateRenderingService.maskPiiForAttachments` closes the gap that used to let unfiltered PII reach attachment providers. |
| **Permission** | `roleRequired` stored, editable, never enforced — no per-caller identity reaches the send path. | **PLATFORM-BLOCKED** | Blocker: no per-user identity pass-through from HyperSense on action submit. Nothing backend-side can enforce a role it's never told. |
| **Object** | `form_fields.field_type` models UI widgets, not HyperSense's real 5 primitives. `hs_field_type` bridge column now exists (V15). | **BUILDABLE-NOW** for the translation layer (shipped) / **PLATFORM-BLOCKED** for true object/array awareness | One schema now serves both the smart dashboard wizard and HyperSense's flat renderer. |
| **Navigation** | `attachmentOptions.includeCaseLink` already puts a case link in outbound email/webhook body. No dynamic-button-from-response inside the panel. | **BUILDABLE-NOW** (in the outbound message) / **PLATFORM-BLOCKED** (in-panel) | Matches the original empirical finding — no dynamic buttons from API response. |
| **Validation** | `field_validation` rows now enforced server-side on submit, not just client-side metadata. | **PLATFORM-BLOCKED** for client-side (greyed-out button) / **BUILDABLE-NOW** — shipped | `FormFieldValidationService` rejects a bad submission with a 400 before it reaches recipient/attachment resolution — degraded UX vs. real-time client validation, but real integrity, zero HyperSense dependency. |
| **Conditional-UI** | `conditional_on_field_key`/`conditional_on_value` exist in schema, unusable by HyperSense. Save-time guard now rejects linking such a schema to a `hypersense_exposed` action (V15). | **PLATFORM-BLOCKED**, hard | No backend workaround possible for HyperSense honoring it — render-time decision. Mitigation shipped: reject the unsafe combination at save time rather than let it silently break. Deeper mitigation (fan a conditional action out into multiple flat actions) still a manual authoring pattern, not automated. |

The throughline: every BUILDABLE-NOW row works today, or with a backend-only
change. Every PLATFORM-BLOCKED row is blocked because it needs HyperSense to
either (a) know who's clicking, or (b) render something other than a flat
field list — exactly the two things the proven constraint rules out.

---

## Registry deltas

### Shipped (V15, this repo)

- **`form_fields.hs_field_type`** (nullable `VARCHAR(20)`) — down-leveled
  `STRING | BOOLEAN | INTEGER | ARRAY | OBJECT` primitive, backfilled from
  existing `field_type` for all current rows. No CHECK constraint (matches
  the `field_validation.validation_type` convention) — validated at the
  application layer instead, so the set can grow without a migration.
- **`notification_action.hypersense_exposed`** (`BOOLEAN NOT NULL DEFAULT
  FALSE`) — marks an action as callable from HyperSense's panel. Every
  existing action keeps behaving exactly as today until explicitly opted in.
  Save-time guard rejects linking such an action to a form schema containing
  a conditional field.
- **`notification_action.context_resolver_key`** (nullable `VARCHAR(60)`) —
  names a future `ContextResolver` bean for the `?caseId=X` schema-fetch
  extension (Platform Ask #2). Column and DTO plumbing shipped; no resolver
  bean exists yet — `null` today means no resolver, current static-schema
  behavior.
- Java model/DTO/controller wiring and React admin UI (`NotificationActions.jsx`,
  `FormSchemas.jsx`) for all three columns above — shipped, compile- and
  build-verified, not yet run against a live database.
- **`UserDirectoryResolver` interface** + `AppMonitoringUserDirectoryResolver`
  implementation (`service/directory/`), backed by a new `user-directory-datasource`
  Hikari pool against `appmonitoringmetricservice` on the already-credentialed
  `172.24.111.102:32704` — `SELECT email_id FROM users WHERE user_name = ?
  AND status = 'active'`, same graceful-skip pattern as the existing
  `case-tbl-datasource` (blank config = every call falls through, app still
  boots cleanly). Wired into the two places that used to duplicate the wrong
  domain-guess logic: `NotificationService.resolveCurrentUserRecipient()`'s
  `acting_username` step (tried after `app_user.email`, before falling
  through to `case_owner_email`), and `CaseWatchScheduler`'s
  `case_owner_email` synthesis from `case_tbl.current_assigned_user` (tried
  before the `owner-email-domain` guess, which now only fires when the
  directory doesn't have that username). **Live-verified** 2026-07-31 against
  the real `appmonitoringmetricservice` DB (standalone run, bypassing the
  app's own local DB which this dev environment can't reach): `pawan` →
  `pawan.tiwari@subex.com`, `rohith.r` → `rohith.raju@subex.com`,
  `fraud_analyst` → `fraud.user@subex.com`, unknown username → graceful
  empty. Not yet verified through the full app (`resolveCurrentUserRecipient`
  end-to-end, `CaseWatchScheduler`'s `case_owner_email` wiring) — that needs
  Flyway/JPA against a live local Postgres, still unavailable here.
- **`EVIDENCE` attachment provider un-stubbed.** New `CaseArtifactExportService`
  (`service/`, mirrors `PrRecordsExportService`'s pattern — own private
  Hikari pool, reusing the existing `case-tbl-datasource` config since it's
  the same `casemanagement` DB `CaseWatchScheduler` already polls)
  implements the verified `case_id → case_workstep → case_artifact →
  case_doc_artifact` join. Returns the single file directly when a case has
  exactly one, zips multiple together (same convention
  `AttachmentOrchestrationService` already uses across providers) — a case
  can have more than one evidence file (verified: `case_id 368755` → 4 real
  files). `EvidenceAttachmentProvider` (`service/attachment/`) is now a thin
  adapter over it, `isAvailable()` reflecting whether `case-tbl-datasource`
  is configured. Description text flags the thin current volume (12/47 rows)
  so operators don't expect broad coverage. **Live-verified** 2026-07-31
  against the real `casemanagement` DB (same standalone run): `case_id
  368755` (4 files) → correctly zipped to `case_evidence_368755.zip`
  (429,335 bytes); `case_id 341730` (1 file) → attached directly as
  `Subscriber info.csv` (6,656 bytes, matching the byte count found during
  the original schema verification); a nonexistent case id → graceful
  failure, not an exception. Not yet verified through the full app (the
  `AttachmentOrchestrationService`/`/api/v1/notify` path) — same Flyway/JPA
  local-Postgres blocker as above.

- **DLP-for-attachments gap fixed.** New `TemplateRenderingService.maskPiiForAttachments(template,
  context)` — attachment providers read the raw context map directly (not
  through `{{variable}}` interpolation), so the `allowed_variables`
  whitelist doesn't apply the same way there (providers legitimately need
  structural lookup keys like `case_id`/`catalog_id` that were never meant
  to appear in a rendered message) — but `pii_mask_fields` still should.
  `NotificationService.notify()` now masks PII-flagged context values before
  handing the map to `attachmentOrchestrationService.generateBundle(...)`
  and the legacy PR-records CSV path, only when a template is in play (no
  template = no `pii_mask_fields` list to apply, unchanged behavior). 3 new
  unit tests, all passing.
- **Server-side `field_validation` enforcement shipped.** New
  `FormFieldValidationService` (REQUIRED/MIN_LENGTH/MAX_LENGTH/PATTERN/MIN/
  MAX/EMAIL_FORMAT) plus `FormValidationException` → 400
  `FORM_VALIDATION_ERROR`. `NotificationService.notify()` validates
  `request.payload()` against the resolved action's `form_schema` before
  proceeding — this closes the "client-side-only, never enforced" gap from
  the Missing Metadata Matrix. Malformed metadata (bad length bound, invalid
  regex, unknown validation type) is logged and skipped rather than
  rejecting the caller's legitimate submission — a metadata-authoring bug
  shouldn't 400 every send against that action. 16 new unit tests, all
  passing (including 3 specifically covering the graceful-skip-on-bad-metadata
  behavior).

### Designed, not yet built

- **`RecipientGroupMember.hsUserRef`** — currently dead. Worth wiring to the
  new `UserDirectoryResolver` once Platform Ask #1 lands, rather than adding
  a new column later.

---

## HyperSense platform asks

Leverage-ranked. Each names exactly what it unlocks, and is scoped as an
incremental change to the existing flat renderer, not a rendering-model
rewrite.

**1. Per-user identity pass-through on action submit.** HyperSense includes
the acting analyst's username (session/JWT claim, however PAS already holds
it) as a fixed, system-injected field on every action POST — not editable,
not a visible form box, always present. Unlocks `roleRequired` enforcement
(metadata already exists, sits unused), accurate audit-log actor attribution,
and automatic `CURRENT_USER` resolution without a manual
`context.acting_username` convention. Smallest platform surface, biggest
correctness gain.

**2. Pass `caseId` on the schema-fetch call.** Concrete, partially-buildable
extension of an endpoint PAS already calls: `GET
/api/v1/actions/{code}/schema?caseId=X`, backed by the `context_resolver_key`
column already shipped (V15). The ask to PAS is narrow — pass a query param
it already has available, not a new capability.

**3. Standardized response surfaced to the analyst.** Whatever the action's
POST target returns in a documented `{status, message}` shape gets shown
verbatim (toast/panel banner), not a generic "submitted" confirmation
regardless of outcome. Closes a real trust gap — doubles as the UX fix for
server-side `field_validation` enforcement (a rejected submission needs to be
visible, not silent).

**4. Generalize the "Add Hotlist" picker exception.** Expose a small enum of
picker types PAS is willing to support (`RECIPIENT_GROUP_PICKER`,
`ATTACHMENT_SCHEMA_PICKER`) reusing whatever internal mechanism already backs
"Add Hotlist." Lowest priority / optional — the platform works without it via
free-text fields carrying a code the brain resolves server-side. Also lowest
value now that internal-recipient resolution doesn't need a picker at all
(handled server-side via the directory mirror).

**5. Read the Analyst Actions button list from `GET /api/v1/actions`.**
Reframed smaller than originally scoped — HS Notify's side is done and
already lookup-token authenticated. The actual ask to PAS is an integration
task on their side (read from this endpoint at startup/refresh instead of
static per-environment config), not a request for new capability.

Not asking for: conditional field visibility, object-type-aware rendering, or
dynamic buttons from arbitrary API responses — those require HyperSense to
grow real form-engine intelligence, contradicting the proven constraint this
whole design works around.

---

## Scope discipline

This document designs the notification platform only. It does not
generalize into "one engine for forms / approvals / workflows / task
assignment" — a second real use case should pull that generalization out
later, not this one.
