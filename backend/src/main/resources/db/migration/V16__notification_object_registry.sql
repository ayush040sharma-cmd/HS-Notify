-- Object Registry (the item flagged as fully unbuilt in the metadata-driven
-- platform audit). Maps a caller-supplied source_type to where that object's
-- data actually lives and which AttachmentProvider key can pull evidence for
-- it, so NotifyRequest.sourceType can drive automatic attachment routing
-- instead of every caller having to already know an attachmentOptions.providers
-- key (see NotificationService.notify / resolveAutoAttachmentProviderKey).
--
-- attachment_provider_key deliberately has no FK — AttachmentProvider keys are
-- a code-level bean registry (AttachmentProviderRegistry), not a DB table, the
-- same convention field_type/validation_type already use in this schema.
--
-- object_type is a natural-key VARCHAR PK (not a surrogate id) because
-- callers reference it directly (it IS the value NotifyRequest.sourceType is
-- looked up by) — no join table needs a numeric id for this row.
CREATE TABLE notification_object_registry (
    object_type              VARCHAR(30) PRIMARY KEY,
    display_name             VARCHAR(150) NOT NULL,
    source_table             VARCHAR(150),
    primary_key_column       VARCHAR(100),
    attachment_provider_key  VARCHAR(60),
    navigation_url_template  VARCHAR(500),
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_by               VARCHAR(100) NOT NULL DEFAULT 'system',
    created_on                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- CASE: verified against the real casemanagement.case_tbl schema
-- (CaseWatchScheduler already polls "SELECT id, ... FROM case_tbl") — id is
-- the real primary key. navigation_url_template reuses the exact pattern
-- already live in hs-notification.case-link.base-url (application.yml) /
-- NotificationService.computeCaseLink, not a newly invented URL.
INSERT INTO notification_object_registry
    (object_type, display_name, source_table, primary_key_column, attachment_provider_key, navigation_url_template, is_active, created_by)
VALUES
    ('CASE', 'HyperSense Case', 'casemanagement.case_tbl', 'id', 'EVIDENCE',
     'https://bscg-presales.subex.com/pas-client/cases/case-investigation/{id}', TRUE, 'system-migration');

-- PR: deliberately NOT a clean mirror of CASE. PR_RECORDS records are pulled
-- from system_pr_results_<catalog_id> (PrRecordsExportService) — a table
-- name that is itself parameterized by catalog_id, and every existing query
-- filters it by case_id, not an independent PR primary key. There is no PR
-- object with its own identity separate from the case it belongs to today.
-- source_table/primary_key_column below document that reality (a template,
-- not a fixed name) rather than inventing a fictional PR entity.
-- navigation_url_template is left NULL: no PR-specific navigation URL exists
-- anywhere in this codebase today (checked application.yml and every
-- template/service) — seeding one would be a guess, not a verified fact.
INSERT INTO notification_object_registry
    (object_type, display_name, source_table, primary_key_column, attachment_provider_key, navigation_url_template, is_active, created_by)
VALUES
    ('PR', 'Pattern Recognition Records', 'system_pr_results_{catalog_id}', 'case_id', 'PR_RECORDS',
     NULL, TRUE, 'system-migration');
