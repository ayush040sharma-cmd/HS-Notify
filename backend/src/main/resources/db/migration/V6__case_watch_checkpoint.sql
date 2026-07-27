-- Persists the poll checkpoint for CaseWatchScheduler (NEW_CASE_ALERT_RULE).
-- Single-row table: the scheduler seeds it to MAX(case_tbl.id) on first run
-- so it never re-sends the existing case_tbl backlog, then advances
-- last_seen_case_id to the highest id it has processed on every poll.
CREATE TABLE case_watch_checkpoint (
    checkpoint_id       BIGSERIAL PRIMARY KEY,
    last_seen_case_id   BIGINT NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
