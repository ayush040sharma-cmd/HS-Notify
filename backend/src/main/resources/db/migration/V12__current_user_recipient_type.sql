-- CURRENT_USER dynamic recipient type: a rule can resolve its TO address at
-- send time from the acting user (analyst-clicked path) instead of a static
-- recipient_group, with a fallback for paths that have no acting user
-- (the case-watch scheduler).
ALTER TABLE app_user ADD COLUMN email VARCHAR(255);

ALTER TABLE notification_rule
    ADD COLUMN recipient_mode VARCHAR(30) NOT NULL DEFAULT 'STATIC_GROUP',
    ADD COLUMN fallback_recipient_group_id BIGINT REFERENCES recipient_group(recipient_group_id);
-- recipient_mode: STATIC_GROUP (existing behavior, default — every existing
-- rule keeps working unchanged) | CURRENT_USER (resolve TO dynamically; see
-- NotificationService.resolveRecipients / CURRENT_USER resolution order in
-- NotificationService.notify / submitRuleBasedNotification).
