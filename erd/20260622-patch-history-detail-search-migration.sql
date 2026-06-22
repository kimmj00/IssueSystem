-- Patch history detail search and upload log migration.
-- Safe to run more than once on PostgreSQL.

BEGIN;

ALTER TABLE issue_case
    ADD COLUMN IF NOT EXISTS category VARCHAR(50);

ALTER TABLE issue_case
    ADD COLUMN IF NOT EXISTS deployment_version VARCHAR(50);

ALTER TABLE issue_case
    ADD COLUMN IF NOT EXISTS completed_date DATE;

CREATE TABLE IF NOT EXISTS patch_history_upload_log (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    saved_count INTEGER NOT NULL,
    excluded_count INTEGER NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_issue_case_category
    ON issue_case (category);

CREATE INDEX IF NOT EXISTS idx_issue_case_deployment_version
    ON issue_case (deployment_version);

CREATE INDEX IF NOT EXISTS idx_issue_case_completed_date
    ON issue_case (completed_date);

COMMIT;
