BEGIN;

ALTER TABLE knowledge_share
    ADD COLUMN IF NOT EXISTS created_by_account_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_knowledge_share_account'
    ) THEN
        ALTER TABLE knowledge_share
            ADD CONSTRAINT fk_knowledge_share_account
            FOREIGN KEY (created_by_account_id) REFERENCES account (id);
    END IF;
END $$;

COMMENT ON COLUMN knowledge_share.created_by_account_id IS '지식을 등록한 계정 ID';

COMMIT;
