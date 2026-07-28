package com.hs.notification.dto;

/** schema is null when the action has no form_schema_id linked yet — not an error, just "no dynamic form defined." */
public record ActionSchemaResponse(String actionCode, FormSchemaResponse schema) {}
