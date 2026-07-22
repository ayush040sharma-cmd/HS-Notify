-- Mailpit has been removed and mail now flows through the real Subex SMTP
-- relay, so the placeholder vendor escalation address from V2 seed data
-- would otherwise receive a real (bouncing) email on the next watchdog
-- escalation. Point it at the real Subex support mailbox instead.
UPDATE watchdog_config
SET escalation_recipients = ARRAY['support.alerts@subex.com']
WHERE escalation_recipients @> ARRAY['support-l1@hs-vendor.example.com'];
