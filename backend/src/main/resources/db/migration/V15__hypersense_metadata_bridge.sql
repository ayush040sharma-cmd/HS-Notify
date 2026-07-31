-- HyperSense Metadata Bridge (v2 design, see HS_NOTIFICATION_V2_METADATA_DESIGN.md).
--
-- The Form Metadata Engine (V8) and Notification Action Registry (V7) were
-- built assuming a smart frontend — the dashboard's own ad-hoc-send wizard,
-- which can render a DROPDOWN widget and honor conditional_on_field_key.
-- HyperSense's Analyst Actions panel cannot: it renders a flat, ordered list
-- of STRING/BOOLEAN/INTEGER/ARRAY/OBJECT-with-nested-list fields as plain
-- text boxes and checkboxes, nothing else. This migration adds the minimum
-- schema needed to let the SAME form_schema serve both consumers, without
-- touching any existing column or row.

-- Down-leveled primitive HyperSense actually renders, independent of the
-- existing widget-oriented field_type. Nullable + no CHECK constraint,
-- matching the field_validation.validation_type convention (V8) — the
-- recognized set (STRING | BOOLEAN | INTEGER | ARRAY | OBJECT) is enforced
-- at the application layer so it can grow without a migration.
ALTER TABLE form_fields
    ADD COLUMN hs_field_type VARCHAR(20);

-- Backfill existing rows from their widget type. FILE_UPLOAD and
-- DYNAMIC_LOOKUP collapse to STRING because HyperSense has no live-picker
-- concept — the brain must pre-resolve those to a plain value before the
-- panel ever renders (see the context_resolver_key mechanism below).
UPDATE form_fields SET hs_field_type = 'STRING'
    WHERE field_type IN ('TEXTBOX', 'TEXTAREA', 'EMAIL', 'DATE', 'DROPDOWN', 'RADIO', 'FILE_UPLOAD', 'DYNAMIC_LOOKUP');
UPDATE form_fields SET hs_field_type = 'BOOLEAN'
    WHERE field_type = 'CHECKBOX';

-- Marks an action as callable from HyperSense's Analyst Actions panel (must
-- degrade gracefully — flat fields only, no conditionals) vs. an action
-- reachable only from the internal dashboard wizard (can keep using the
-- fuller widget/conditional metadata as-is). Defaults false: every existing
-- action keeps behaving exactly as it does today until explicitly opted in.
ALTER TABLE notification_action
    ADD COLUMN hypersense_exposed BOOLEAN NOT NULL DEFAULT FALSE;

-- Names a ContextResolver bean (same registry pattern as AttachmentProvider/
-- ChannelSender) invoked by GET /api/v1/actions/{code}/schema?caseId=X to
-- merge {fieldKey: resolvedDefaultValue} into the schema response before
-- HyperSense ever renders it. Null means no resolver — schema is returned
-- as static metadata only, today's behavior.
ALTER TABLE notification_action
    ADD COLUMN context_resolver_key VARCHAR(60);
