-- Comments for patch history detail search and upload log DB changes.

BEGIN;

COMMENT ON COLUMN issue_case.category IS 'Patch history category';
COMMENT ON COLUMN issue_case.deployment_version IS 'Patch history deployment version';
COMMENT ON COLUMN issue_case.completed_date IS 'Patch history completed date';

COMMENT ON TABLE patch_history_upload_log IS 'Patch history Excel upload log';
COMMENT ON COLUMN patch_history_upload_log.id IS 'Patch history upload log ID';
COMMENT ON COLUMN patch_history_upload_log.file_name IS 'Uploaded original file name';
COMMENT ON COLUMN patch_history_upload_log.saved_count IS 'Saved patch history row count';
COMMENT ON COLUMN patch_history_upload_log.excluded_count IS 'Excluded duplicate or invalid row count';
COMMENT ON COLUMN patch_history_upload_log.created_at IS 'Created timestamp';
COMMENT ON COLUMN patch_history_upload_log.updated_at IS 'Updated timestamp';

COMMIT;
